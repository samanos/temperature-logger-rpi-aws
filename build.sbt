organization := "io.github.samanos"
name := "tlog"

scalaVersion := "2.11.8"
scalacOptions += "-feature"

val Akka = "2.4.3"

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-stream"                     % Akka,
  "io.spray"             %% "spray-json"                      % "1.3.2",
  "com.github.pathikrit" %% "better-files"                    % "2.15.0",
  "org.eclipse.paho"      % "org.eclipse.paho.client.mqttv3"  % "1.0.2",
  "org.bouncycastle"      % "bcprov-jdk15on"                  % "1.54",
  "org.bouncycastle"      % "bcpkix-jdk15on"                  % "1.54",
  "com.typesafe.akka"    %% "akka-testkit"                    % Akka      % Test,
  "com.typesafe.akka"    %% "akka-stream-testkit"             % Akka      % Test,
  "org.scalatest"        %% "scalatest"                       % "2.2.6"   % Test,
  "org.scalamock"        %% "scalamock-scalatest-support"     % "3.2.2"   % Test
)

enablePlugins(GitVersioning)
git.useGitDescribe := true

enablePlugins(JavaAppPackaging)

initialCommands in console := """import io.github.samanos.tlog._"""
