name := "atm-simulator"

version := "0.1"

scalaVersion := "2.12.8"

val AkkaHttpVersion = "10.1.7"
val AkkaVersion = "2.5.22"

libraryDependencies ++= Seq(
  "com.github.pureconfig" %% "pureconfig" % "0.10.1",
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.json4s" %% "json4s-jackson" % "3.6.5",
  "org.json4s" %% "json4s-ext" % "3.6.5",
  "de.heikoseeberger" %% "akka-http-json4s" % "1.25.2",

  "com.softwaremill.macwire" %% "macros" % "2.3.1" % "provided",
  "com.softwaremill.macwire" %% "macrosakka" % "2.3.1" % "provided",

  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,

)