organization := "io.github.samanos"
name := "tlog"

scalaVersion := "2.11.8"
scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-http-spray-json-experimental" % "2.4.2",
  "com.github.pathikrit" %% "better-files"                      % "2.15.0",
  "org.scalatest"        %% "scalatest"                         % "2.2.6" % "test"
)

enablePlugins(GitVersioning)
git.useGitDescribe := true

enablePlugins(JavaAppPackaging)

initialCommands in console := """import io.github.samanos.tlog._"""
