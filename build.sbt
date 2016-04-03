organization := "io.github.samanos"
name := "tlog"

scalaVersion := "2.11.8"
scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.typesafe.akka"    %% "akka-http-spray-json-experimental" % "2.4.2",
  "com.github.pathikrit" %% "better-files"                      % "2.15.0",
  "com.typesafe.akka"    %% "akka-testkit"                      % "2.4.2"    % Test,
  "com.typesafe.akka"    %% "akka-stream-testkit"               % "2.4.2"    % Test,
  "org.scalatest"        %% "scalatest"                         % "2.2.6"    % Test,
  "org.scalamock"        %% "scalamock-scalatest-support"       % "3.2.2"    % Test,
  "com.amazonaws"         % "DynamoDBLocal"                     % "1.10.5.1" % Test
)

resolvers += "DynamoDB Local" at "http://dynamodb-local.s3-website-us-west-2.amazonaws.com/release"

enablePlugins(GitVersioning)
git.useGitDescribe := true

enablePlugins(JavaAppPackaging)

initialCommands in console := """import io.github.samanos.tlog._"""
