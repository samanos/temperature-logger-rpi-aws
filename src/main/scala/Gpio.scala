package io.github.samanos.tlog

import akka.actor._
import better.files._
import com.typesafe.config.Config

import scala.language.postfixOps

object Gpio {
  object Port extends Enumeration {
    type Port = Value
    val Gpio17 = Value("17")
    val Gpio22 = Value("22")
    val Gpio27 = Value("27")
  }

  object Direction extends Enumeration {
    type Direction = Value
    val Out = Value("out")
    val In = Value("in")
  }

  def apply()(implicit sys: ActorSystem) =
    sys.asInstanceOf[ExtendedActorSystem].dynamicAccess.createInstanceFor[Gpio](
      sys.settings.config.getString("tlog.gpio.class"),
      Vector(classOf[Config] -> sys.settings.config.getConfig("tlog.gpio"))).get
}

trait Gpio {
  import Gpio.Port.Port
  import Gpio.Direction.Direction

  def export(port: Port): Unit
  def direction(port: Port, direction: Direction): Unit
  def value(port: Port, value: String): Unit
  def led(port: Port, on: Boolean): Unit
  def temperature: Iterator[Double]
}

class Rpi1Gpio(conf: Config) extends Gpio {
  import Gpio.Direction._
  import Gpio.Port._

  private final val GpioPath = conf.getString("gpio")
  private final val OneWire = conf.getString("w1")

  import scala.concurrent.ExecutionContext.Implicits.global

  def export(port: Port) =
    if (GpioPath / s"gpio$port" notExists)
      port.toString >>: GpioPath / "export"

  def direction(port: Port, direction: Direction) = {
    val directionFile = GpioPath / s"gpio$port" / "direction"
    if (directionFile.exists && directionFile.contentAsString != direction)
      direction.toString `>:` GpioPath / s"gpio$port" / "direction"
  }

  def value(port: Port, value: String) = {
    val valueFile = GpioPath / s"gpio$port" / "value"
    if (valueFile.exists && valueFile.contentAsString != value)
      value `>:` GpioPath / s"gpio$port" / "value"
  }

  def led(port: Port, on: Boolean) = {
    export(port)
    direction(port, Out)
    value(port, if (on) "1" else "0")
  }

  def temperature = {
    val matcher = OneWire.toFile.pathMatcher(File.PathMatcherSyntax.glob)("**/w1_slave")
    OneWire.toFile.walk(maxDepth=2)(File.VisitOptions.follow).filter(file => matcher.matches(file.path)).flatMap { sensor =>
      sensor.contentAsString match {
        case r"(?ms).*t=(\d+)$degrees.*" => List(degrees.toDouble / 1000)
        case _ => List.empty
      }
    }
  }
}

class ConsoleGpio(conf: Config) extends Gpio {
  import Gpio.Direction._
  import Gpio.Port._

  def export(port: Port) = println(s"Exported $port")
  def direction(port: Port, direction: Direction) = println(s"Changing $port direction to $direction")
  def value(port: Port, value: String) = println(s"Changing $port value to $value")
  def led(port: Port, on: Boolean) = println(s"Turning led $port to $on")
  def temperature = Vector.fill(2)(20 + scala.math.random).toIterator
}
