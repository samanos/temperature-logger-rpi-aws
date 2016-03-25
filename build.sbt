organization := "io.github.samanos"
name := "tlog"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.2"
)

enablePlugins(GitVersioning)
git.useGitDescribe := true

enablePlugins(JavaAppPackaging)

initialCommands in console := """import io.github.samanos.tlog._"""
