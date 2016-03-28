package io.github.samanos.tlog

import scala.io.StdIn

object Main extends App {

  val app = args.toList match {
    case app :: rest => app
    case Nil => StdIn.readLine("What App do you want to run [reporter, repl]: ")
  }

  app match {
    case "reporter" => Reporter.run
    case "repl" => Repl.run
    case _ =>
  }

}
