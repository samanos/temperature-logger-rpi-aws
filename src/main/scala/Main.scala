package io.github.samanos.tlog

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToRequestMarshaller
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.Try

import spray.json._

object Main extends App {

  case class Payload(value1: Double, value2: Double)

  object JsonSupport extends DefaultJsonProtocol {
    implicit val payload = jsonFormat2(Payload)
  }
  import JsonSupport._

  implicit val sys = ActorSystem("Main")
  implicit val mat = ActorMaterializer()

  import sys.dispatcher

  val p = implicitly[ToRequestMarshaller[Payload]]
  //p.

  val interval = sys.settings.config.getDuration("tlog.interval").toScala
  val makerkey = sys.settings.config.getString("tlog.maker-key")
  val report = Uri(sys.settings.config.getString("tlog.report-url"))

  val gpio = Gpio(sys.settings.config)
  val pool = Http().cachedHostConnectionPool[Int](report.authority.host.toString)

  Source.tick(interval, interval, ())
    .map(_ => gpio.temperature.toList)
    .map(t => Payload(t.headOption.getOrElse(0), t.tail.headOption.getOrElse(0)))
    .map(p => HttpRequest(uri = report.path.toString + makerkey, entity = ???))


  //val responseFuture: Future[(Try[HttpResponse], Int)] = Source.single() -> 42)
  //  .via(pool)
  //  .runWith(Sink.head)

  //Await.ready(responseFuture, 10.seconds)

}
