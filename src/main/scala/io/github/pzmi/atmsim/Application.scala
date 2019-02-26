package io.github.pzmi.atmsim

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._

object Application extends App with ActorModule with ServerModule with StrictLogging {
  //  Simulation.start(1L, 1000, 1000000)

  val appRoute = getFromResource("webapp/index.html")
  val staticRoute = pathPrefix("static")(getFromResourceDirectory("webapp/static"))

  var inSink = Sink.foreach((m: Message) => logger.info(s"Message: $m"))
  var outSource = Source.tick(1 second, 1 second, TextMessage("ping"))
  val websocket = path("websocket") {
    extractUpgradeToWebSocket { upgrade => complete(upgrade.handleMessagesWithSinkSource(inSink, outSource))
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
