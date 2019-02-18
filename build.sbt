name := "atm-simulator"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.github.pureconfig" %% "pureconfig" % "0.10.1",
  "com.typesafe.akka" %% "akka-actor" % "2.5.20",
  "com.typesafe.akka" %% "akka-stream" % "2.5.20",
  "com.typesafe.akka" %% "akka-http" % "10.1.7",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",

  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,

  "com.softwaremill.macwire" %% "macros" % "2.3.1" % "provided",
  "com.softwaremill.macwire" %% "macrosakka" % "2.3.1" % "provided"
)