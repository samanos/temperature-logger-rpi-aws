package io.github.samanos.tlog

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.testkit.TestSubscriber
import akka.testkit.EventFilter

import com.typesafe.config.ConfigFactory

import org.eclipse.paho.client.mqttv3.MqttMessage
import org.scalatest.concurrent.ScalaFutures
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.util._

import java.nio.charset.StandardCharsets

class ReporterSpec extends WordSpec with Matchers with MockFactory with ScalaFutures {

  "reporter" should {

    "log" when {

      "not able to read temperature" in withFixture { gpio => implicit sys => implicit mat => implicit ec =>
        (gpio.temperature _).expects().returning(Iterator.empty)

        EventFilter.warning(start = "Unable to read temperature", occurrences = 1) intercept {
          Source.single(()).runWith(Reporter.reporterStream(gpio, blackholeUpload))
        }
      }

    }

    "blink red led" when {

      "tempreture was read" in withFixture { gpio => implicit sys => implicit mat => implicit ec =>
        (gpio.temperature _).expects().returning(List(20.0, 21,0).toIterator)
        inSequence {
          (gpio.led _).expects(Reporter.Red, true)
          (gpio.led _).expects(Reporter.Red, false)
        }

        val done = Source.single(()).runWith(Reporter.reporterStream(gpio, blackholeUpload))
        Await.ready(done, 1.second)
      }
    }

    "blink green led" when {

      "measurement was sent" in withFixture { gpio => implicit sys => implicit mat => implicit ec =>
        (gpio.temperature _).expects().returning(List(20.0, 21,0).toIterator)
        (gpio.led _).expects(Reporter.Red, *).anyNumberOfTimes
        inSequence {
          (gpio.led _).expects(Reporter.Green, true)
          (gpio.led _).expects(Reporter.Green, false)
        }

        val done = Source.single(()).runWith(Reporter.reporterStream(gpio, inmemUpload))
        Await.ready(done, 1.second)
      }

    }

    "prepare upload request" in withFixture { gpio => implicit sys => implicit mat => implicit ec =>
      (gpio.temperature _).expects().returning(List(20.0, 21,0).toIterator)
      (gpio.led _).expects(*, *).anyNumberOfTimes

      val testSub = TestSubscriber.probe[MqttMessage]
      Source.single(()).runWith(Reporter.reporterStream(gpio, Flow.fromSinkAndSource(Sink.fromSubscriber(testSub), Source.empty)))

      val request = testSub.requestNext()
      (new String(request.getPayload, StandardCharsets.UTF_8)) shouldBe """{"value1":20.0,"value2":21.0}"""
    }
  }

  def blackholeUpload(implicit ec: ExecutionContext) = Flow.fromSinkAndSourceMat(Sink.head[MqttMessage], Source.maybe[Done]) {
    (future, promise) => future.onComplete { _ =>
      promise.complete(Success(None))
    }
  }
  def inmemUpload = Flow[MqttMessage].map { _ => Done }

  def withFixture(test: Gpio => ActorSystem => Materializer => ExecutionContext => Any) {
    val conf = ConfigFactory.parseString("""
      akka.loggers = [akka.testkit.TestEventListener]
      tlog {
        led-blink-duration = 0 seconds
      }
    """).withFallback(ConfigFactory.load)
    implicit val sys = ActorSystem("ReporterSpec", conf)
    implicit val mat = ActorMaterializer {
      ActorMaterializerSettings(sys).withSupervisionStrategy { ex =>
        sys.log.warning(s"Error in the stream: ${ex.nameAndMessage}. Restarting stream.")
        Supervision.stop
      }
    }
    val gpio = mock[Gpio]
    try {
      test(gpio)(sys)(mat)(sys.dispatcher)
    } finally {
      sys.terminate()
    }
  }
}
