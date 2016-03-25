package io.github.samanos.tlog

import scala.io._
import scala.util._

object Main extends App {

  def repl(main: String => Option[String]) = {
    @annotation.tailrec
    def action(): Unit = Try(main(StdIn.readLine)) match {
      case Success(Some(output)) => println(output); action()
      case Success(None) => // stop repl
      case Failure(ex) => println(s"Unrecognized input: $ex. Type 'q' to quit."); action()
    }
    action()
  }

  println("Temperature logger REPL:")

  repl {
    case "led_on" => Gpio.led(Gpio.Port.Gpio17, true); Some("turning led on")
    case "led_off" => Gpio.led(Gpio.Port.Gpio17, false); Some("turning led off")
    case "temp" => println(Gpio.temperature); Some("printing temp")
  }

}
