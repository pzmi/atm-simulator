package io.github.pzmi.atmsim

import java.nio.file.Paths

import akka.actor.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Framing, Sink}
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._


object Application extends App with ActorModule with ServerModule with StrictLogging {
  Simulation.start(1L, 1000, 100)

  val appRoute = getFromResource("webapp/index.html")
  val staticRoute = pathPrefix("static")(getFromResourceDirectory("webapp/static"))

  var fromClientSink = Sink.foreach((m: Message) => logger.info(s"Message: $m"))

  private val wsActorRef: ActorRef = system.actorOf(WsActor.props())
  var wsActorSink = Sink.foreachAsync(1) { m: Message =>
    import akka.pattern.ask
    implicit val askTimeout: Timeout = Timeout(30 seconds)

    (wsActorRef ? m).map(_ => ())
  }

  //  var outSource = Source.tick(1 second, 1 second, TextMessage("ping"))

  private def fileSource = {
    val file = Paths.get("output.txt")
    FileIO.fromPath(file)
      .via(Framing
        .delimiter(ByteString("\n"), 4096, true)
        .map(_.utf8String))
      .map(TextMessage(_))
  }


  val websocket = path("websocket") {
    extractUpgradeToWebSocket { upgrade =>
      val toClientSource = fileSource
      complete(upgrade.handleMessagesWithSinkSource(wsActorSink, toClientSource))
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
