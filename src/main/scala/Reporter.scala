package io.github.samanos.tlog

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model._
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString

import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

object Reporter {

  case class Payload(value1: Double, value2: Double)

  object JsonSupport extends DefaultJsonProtocol {
    implicit val payload = jsonFormat2(Payload)
  }
  import JsonSupport._

  final val Red = Gpio.Port.Gpio27
  final val Green = Gpio.Port.Gpio17

  def run = {
    implicit val sys = ActorSystem("Main")
    implicit val mat = ActorMaterializer {
      ActorMaterializerSettings(sys).withSupervisionStrategy { ex =>
        sys.log.warning(s"Error in the stream: ${ex.nameAndMessage}. Restarting stream.")
        Supervision.restart
      }
    }

    import sys.dispatcher

    val interval = sys.settings.config.getDuration("tlog.report-interval").toScala
    val makerkey = sys.settings.config.getString("tlog.maker-key")
    val reportTo = Uri(sys.settings.config.getString("tlog.report-url") + makerkey)

    val gpio = Gpio(sys.settings.config)
    val pool = Http().cachedHostConnectionPool[Int](reportTo.authority.host.toString)

    sys.log.info("Starting reporter stream.")

    Source.tick(interval, interval, ()).runWith(reporterStream(gpio, reportTo, pool))
  }

  def reporterStream(gpio: Gpio, reportTo: Uri, pool: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), _])(implicit ec: ExecutionContext, sys: ActorSystem, mat: Materializer) =
    Flow[Unit]
      .map(_ => gpio.temperature.toList)
      .mapConcat {
        case value :: Nil => List(Payload(value, 0))
        case value1 :: value2 :: Nil => List(Payload(value1, value2))
        case value1 :: value2 :: rest => List(Payload(value1, value2))
        case Nil => sys.log.warning("Unable to read temperature. Check if the module is connected properly."); Nil
      }
      .mapAsync(parallelism = 1)(blink(gpio, Red))
      .mapAsync(parallelism = 1)(p => Marshal((HttpMethods.POST, reportTo, p)).to[HttpRequest])
      .map(_ -> 42)
      .via(pool)
      .mapAsync(parallelism = 1) {
        case (Success(response), _) =>
          Unmarshal(response).to[String].map {
            case msg => Success(msg)
          } recover {
            case t => Failure(t)
          }
        case (Failure(t), _) => Future.successful(Failure(t))
      }
      .mapConcat {
        case Success(message) if message.contains("Congratulations!") => Done :: Nil
        case Success(message) => sys.log.warning(s"Unexpected response message: $message"); Nil
        case Failure(ex) => sys.log.warning(s"Failure when parsing response message: ${ex.nameAndMessage}"); Nil
      }
      .mapAsync(parallelism = 1)(blink(gpio, Green))
      .toMat(Sink.ignore)(Keep.right)

  def blink[T](gpio: Gpio, led: Gpio.Port.Port)(payload: T)(implicit sys: ActorSystem) = {
    import sys.dispatcher

    val duration = sys.settings.config.getDuration("tlog.led-blink-duration").toScala
    gpio.led(led, true)

    val completion = Promise[T]
    sys.scheduler.scheduleOnce(duration) {
      gpio.led(led, false)
      completion.success(payload)
    }
    completion.future
  }
}
