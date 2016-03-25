package io.github.samanos.tlog

import java.io.InputStream
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import akka.util.ByteString

import scala.concurrent._
import scala.sys.process._
import scala.util.{ Failure, Success }

object Gpio {

  import scala.concurrent.ExecutionContext.Implicits.global

  object Port extends Enumeration {
    type Port = Value
    val Gpio17 = Value("17")
    val Gpio22 = Value("22")
    val Gpio27 = Value("27")
  }
  import Port._

  object Direction extends Enumeration {
    type Direction = Value
    val Out = Value("out")
    val In = Value("in")
  }
  import Direction._

  def export(port: Port): Future[Unit] =
    if (!Files.exists(Paths.get(s"/sys/class/gpio/gpio$port"))) {
      s"echo $port" #| "sudo cp /dev/stdin /sys/class/gpio/export" runNoOutput
    } else {
      Future.successful()
    }

  def direction(port: Port, direction: Direction): Future[Unit] = {
    if (slurp(s"/sys/class/gpio/gpio$port/direction") != direction) {
      s"echo $direction"#| s"sudo cp /dev/stdin /sys/class/gpio/gpio$port/direction" runNoOutput
    } else {
      Future.successful()
    }
  }

  def value(port: Port, value: String): Future[Unit] = {
    if (slurp(s"/sys/class/gpio/gpio$port/value") != value) {
      s"echo $value"#| s"sudo cp /dev/stdin /sys/class/gpio/gpio$port/value" runNoOutput
    } else {
      Future.successful()
    }
  }

  def led(port: Port, on: Boolean) = for {
    _ <- export(port).transform(identity, printingIdentity)
    _ <- direction(port, Out).transform(identity, printingIdentity)
    _ <- value(port, if (on) "1" else "0").transform(identity, printingIdentity)
  } yield ()

  def temperature: List[Double] = {
    def allFilesIn(path: Path, glob: String) = {
      val matcher = FileSystems.getDefault().getPathMatcher(s"glob:$glob")
      var files: List[Path] = List.empty

      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          if (matcher.matches(file.getFileName)) {
            files = file.getFileName +: files
          }
          FileVisitResult.CONTINUE
        }
      })
      files
    }
    allFilesIn(Paths.get("/sys/bus/w1/devices/"), "28-*/w1_slave").flatMap { sensorFile =>
      slurp(sensorFile) match {
        case r"(?ms).*t=(\d+)$degrees" => List(degrees.toDouble / 1000)
        case _ => List.empty
      }
    }
  }

  private implicit class RichProcessBuilder(pb: ProcessBuilder) {
    def runNoOutput() = {

      val stdOut = Promise[ByteString]
      val stdErr = Promise[ByteString]

      def outputGrabber(contentsPromise: Promise[ByteString])(input: InputStream) = {
        try {
          var contents = ByteString.empty
          var continue = true
          while (continue) {
            val byte = input.read()
            if (byte != -1) {
              contents = contents ++ ByteString(byte)
            }
            else {
              continue = false
            }
          }
          input.close()
          contentsPromise.success(contents)
        }
        catch {
          case ex: IOException => contentsPromise.failure(ex)
        }
      }

      val process = pb run new ProcessIO(_.close, outputGrabber(stdOut), outputGrabber(stdErr))
      process.exitValue()

      val result = Promise[Unit]

      stdOut.future.onComplete { stdOutResult =>
        stdErr.future.onComplete { stdErrResult =>
          (stdOutResult, stdErrResult) match {
            case (Success(out), Success(err)) if out.size == 0 && err.size == 0 => result.success()
            case (Success(out), Success(err)) if out.size != 0 && err.size == 0 => result.failure(new Error(s"Got unexpeted stdout: $out"))
            case (Success(out), Success(err)) if out.size == 0 && err.size != 0 => result.failure(new Error(s"Got unexpeted stderr: $err"))
            case (Success(out), Success(err)) if out.size != 0 && err.size != 0 => result.failure(new Error(s"Got unexpeted stdout: $out and stderr: $err"))
            case (Success(_), Failure(err)) => result.failure(new Error(s"Exception while reading stderr: ${err.getMessage}", err))
            case (Failure(out), Success(_)) => result.failure(new Error("Exception while reading stdout: ${out.getMessage}", out))
            case (Failure(out), Failure(err)) => result.failure(new Error(s"Exception while reading stdout: ${out.getMessage} and stderr: ${err.getMessage}", out))
          }
        }
      }

      result.future
    }
  }

  private def slurp(filename: String): String = {
    val source = scala.io.Source.fromFile(filename)
    try source.mkString finally source.close()
  }

  private def slurp(path: Path): String = slurp(path.toString)

  private def printingIdentity[T](t: T) = {
    println(t)
    t
  }

  implicit class Regex(sc: StringContext) {
    def r = new scala.util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }

}
