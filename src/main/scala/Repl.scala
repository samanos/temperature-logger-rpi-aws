package io.github.samanos.tlog

import com.typesafe.config.ConfigFactory

object Repl extends App {

  val conf = ConfigFactory.load()
  val gpio = new Gpio(conf)

  val leds = Map(
    "green" -> Gpio.Port.Gpio17,
    "red" -> Gpio.Port.Gpio27
  )

  println("Temperature logger REPL:")
  repl {
    case r"(red|green)$led (on|off)$status" => gpio.led(leds(led), status == "on"); Some(s"Turning $led led $status.")
    case "temp" => Some(s"Current temperature: ${gpio.temperature.toList}")
    case "q" => None
  }

}
