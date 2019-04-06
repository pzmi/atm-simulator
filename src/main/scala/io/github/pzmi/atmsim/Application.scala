package io.github.pzmi.atmsim

import java.nio.file.Paths
import java.time.{LocalDate, LocalDateTime, LocalTime}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Flow, Framing, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.language.postfixOps


object Application extends App with ActorModule with ServerModule with StrictLogging {
  val startDate = LocalDateTime.of(LocalDate.of(2019, 3, 3), LocalTime.of(11, 11))
  val days = 1
  private val hoursPerDay = 24
  Simulation.start(1L, 1000, 100, startDate, startDate.plusHours(hoursPerDay * days))

  val appRoute = getFromResource("webapp/index.html")
  val staticRoute = pathPrefix("static")(getFromResourceDirectory("webapp/static"))

  var fromClientSink = Sink
    .foreach((m: Message) => logger.info(s"Message received from client: $m"))

  var pingSource = Source.tick(1 second, 10 second, TextMessage("ping"))

  private def fileSource = {
    val file = Paths.get("output.txt")
    FileIO.fromPath(file)
      .via(Framing
        .delimiter(ByteString("\n"), 4096, allowTruncation = true)
        .map(_.utf8String))
      .grouped(1000)
      .map(_.mkString(","))
      .map(concatenated => s"[$concatenated]")
      .wireTap(_ => logger.info("Sending message to client"))
      .map(TextMessage(_))
      .merge(pingSource)
  }

  private val onDemandDataFlow = Flow[Message]
    .zip(fileSource)
    .wireTap(m => logger.info(s"Message received from client: ${m._1}"))
    .map(_._2)

  val websocket = path("websocket") {
    extractUpgradeToWebSocket { upgrade =>
      complete(upgrade.handleMessages(onDemandDataFlow))
    }
  }

  private val host = "localhost"
  private val port = 8080
  val bindingFuture = Http().bindAndHandle(websocket ~ staticRoute ~ appRoute, host, port)

  logger.info(s"Starting server on http://$host:$port")

  sys.addShutdownHook({
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  })
}
