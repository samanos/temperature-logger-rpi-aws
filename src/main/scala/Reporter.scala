package io.github.samanos.tlog

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString

import org.eclipse.paho.client.mqttv3.MqttMessage
import spray.json._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

import java.nio.charset.StandardCharsets

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

    val gpio = Gpio()
    val upload = Mqtt.connection(sys.settings.config.getConfig("tlog.mqtt"))

    sys.log.info("Starting reporter stream.")

    Source.tick(interval, interval, ()).runWith(reporterStream(gpio, upload))
  }

  def reporterStream(gpio: Gpio, upload: Flow[MqttMessage, Done, _])(implicit ec: ExecutionContext, sys: ActorSystem, mat: Materializer) =
    Flow[Unit]
      .map(_ => gpio.temperature.toList)
      .mapConcat {
        case value :: Nil => List(Payload(value, 0))
        case value1 :: value2 :: Nil => List(Payload(value1, value2))
        case value1 :: value2 :: rest => List(Payload(value1, value2))
        case Nil => sys.log.warning("Unable to read temperature. Check if the module is connected properly."); Nil
      }
      .mapAsync(parallelism = 1)(blink(gpio, Red))
      .map(_.toJson.toString)
      .map(json => new MqttMessage(json.getBytes(StandardCharsets.UTF_8)))
      .via(upload)
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
