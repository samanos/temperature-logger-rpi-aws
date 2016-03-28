package io.github.samanos.tlog

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString

import spray.json._

object Reporter {

  case class Payload(value1: Double, value2: Double)

  object JsonSupport extends DefaultJsonProtocol {
    implicit val payload = jsonFormat2(Payload)
  }
  import JsonSupport._

  def run = {
    implicit val sys = ActorSystem("Main")
    implicit val mat = ActorMaterializer()

    import sys.dispatcher

    val interval = sys.settings.config.getDuration("tlog.interval").toScala
    val makerkey = sys.settings.config.getString("tlog.maker-key")
    val reportTo = Uri(sys.settings.config.getString("tlog.report-url") + makerkey)

    val gpio = Gpio(sys.settings.config)
    val pool = Http().cachedHostConnectionPool[Int](reportTo.authority.host.toString)

    Source.tick(interval, interval, ())
      .map(_ => gpio.temperature.toList)
      .mapConcat {
        case value :: Nil => List(Payload(value, 0))
        case value1 :: value2 :: Nil => List(Payload(value1, value2))
        case value1 :: value2 :: rest => List(Payload(value1, value2))
        case Nil => sys.log.warning("Unable to read temperature. Check if the module is connected properly."); Nil
      }
      .mapAsync(parallelism = 1)(p => Marshal((HttpMethods.POST, reportTo, p)).to[HttpRequest])
      .map(_ -> 42)
      .runForeach {
        case (response, _) => response.entity.dataBytes.runWith(Sink.ignore)
    }
  }
}
