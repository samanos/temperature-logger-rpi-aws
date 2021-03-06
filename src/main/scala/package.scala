package io.github.samanos

import akka.event.Logging

import scala.io._
import scala.util._

package object tlog {

  def repl(main: String => Option[String]) = {
    @annotation.tailrec
    def action(): Unit = Try(main(StdIn.readLine)) match {
      case Success(Some(output)) => println(output); action()
      case Success(None) => // stop repl
      case Failure(ex) => println(s"Unrecognized input: $ex. Type 'q' to quit."); action()
    }
    action()
  }

  implicit class Regex(sc: StringContext) {
    def r = new scala.util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }

  implicit class JavaDurationOps(d: java.time.Duration) {
    def toScala = scala.concurrent.duration.Duration.fromNanos(d.toNanos)
  }

  implicit class ThrowableOps(t: Throwable) {
    def nameAndMessage = s"${Logging.simpleName(t)}: ${t.getMessage}"
  }

}
