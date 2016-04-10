package io.github.samanos.tlog

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.testkit.TestSubscriber
import akka.testkit.EventFilter

import com.typesafe.config.ConfigFactory

import org.scalatest.concurrent.ScalaFutures
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.util._

class ReporterSpec extends WordSpec with Matchers with MockFactory with ScalaFutures {

  "reporter" should {

    "log" when {

      "not able to read temperature" in withFixture { gpio => implicit sys => implicit mat => implicit ec =>
        (gpio.temperature _).expects().returning(Iterator.empty)

        EventFilter.warning(start = "Unable to read temperature", occurrences = 1) intercept {
          Source.single(()).runWith(Reporter.reporterStream(gpio, Uri(), inmemHttp()))
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

        val done = Source.single(()).runWith(Reporter.reporterStream(gpio, Uri(), inmemHttp()))
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

        val done = Source.single(()).runWith(Reporter.reporterStream(gpio, Uri("/endpoint"), inmemHttp("Congratulations!")))
        Await.ready(done, 1.second)
      }

    }

    "prepare http request" in withFixture { gpio => implicit sys => implicit mat => implicit ec =>
      (gpio.temperature _).expects().returning(List(20.0, 21,0).toIterator)
      (gpio.led _).expects(*, *).anyNumberOfTimes

      val reportTo = Uri("/report/url")
      val testSub = TestSubscriber.probe[(HttpRequest, Int)]
      Source.single(()).runWith(Reporter.reporterStream(gpio, reportTo, Flow.fromSinkAndSource(Sink.fromSubscriber(testSub), Source.empty)))

      val (httpRequest, _) = testSub.requestNext()
      httpRequest.method shouldBe HttpMethods.POST
      httpRequest.uri shouldBe reportTo
      Unmarshal(httpRequest).to[String].futureValue shouldBe """|{
                                                                |  "value1": 20.0,
                                                                |  "value2": 21.0
                                                                |}""".stripMargin
    }
  }

  def inmemHttp(response: String = "") = Flow[(HttpRequest, Int)].map[(Try[HttpResponse], Int)] {
    case (_, rId) => (Success(HttpResponse(StatusCodes.OK, entity = response)), rId)
  }

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
