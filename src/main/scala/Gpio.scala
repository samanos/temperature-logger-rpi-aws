package io.github.samanos.tlog
package gpio

import java.io.InputStream
import java.io.IOException
import java.nio.file.{ Files, Paths }

import akka.util.ByteString

import scala.concurrent._
import scala.sys.process._
import scala.util.{ Failure, Success }

object GpioControl {

  def export(port: Int): Future[Unit] =
    if (!Files.exists(Paths.get(s"/sys/class/gpio/gpio$port"))) {
      s"echo $port" #| "sudo cp /dev/stdin /sys/class/gpio/export" runNoOutput
    } else {
      Future.successful()
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

      import scala.concurrent.ExecutionContext.Implicits.global
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

}
