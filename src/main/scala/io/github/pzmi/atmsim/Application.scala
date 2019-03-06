package io.github.pzmi.atmsim

import java.nio.file.Paths

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._

object Application extends App with ActorModule with ServerModule with StrictLogging {
    Simulation.start(1L, 1000, 100)

  val appRoute = getFromResource("webapp/index.html")
  val staticRoute = pathPrefix("static")(getFromResourceDirectory("webapp/static"))

  var inSink = Sink.foreach((m: Message) => logger.info(s"Message: $m"))

  val file = Paths.get("output.txt")
  val fileSource = FileIO.fromPath(file)
    .via(Framing
      .delimiter(ByteString("\n"), 4096, true)
      .map(_.utf8String))
    .map(TextMessage(_))



  var outSource = Source.tick(1 second, 1 second, TextMessage("ping"))
  val websocket = path("websocket") {
    extractUpgradeToWebSocket { upgrade => complete(upgrade.handleMessagesWithSinkSource(inSink, fileSource))
    }
  }

  private val host = "localhost"
  private val port = 8080
  val bindingFuture = Http().bindAndHandle(websocket ~ staticRoute ~ appRoute, host, port)

  logger.info(s"Starting server on $host:$port")

  sys.addShutdownHook({
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  })
}
