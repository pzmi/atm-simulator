package io.github.pzmi.atmsim

import java.nio.file.Paths

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, RejectionHandler, Route}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Framing, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats, jackson}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

trait ServerModule extends ActorModule {

  import com.softwaremill.macwire._

  lazy val server = wire[Server]
}

class Server(implicit private val materializer: Materializer,
             implicit private val executionContext: ExecutionContext) extends StrictLogging with Json4sSupport {
  implicit val formats: AnyRef with Formats = DefaultFormats + InstantSerializer + DistributionSerializer
  implicit val serialization: Serialization.type = jackson.Serialization

  private val configString: String = scala.io.Source.fromResource("config.json").getLines.mkString("\n")
  val config: Config = Serialization.read[Config](configString)

  implicit def rejectionHandler: RejectionHandler =
    RejectionHandler.newBuilder().handleAll[MethodRejection] { rejections =>
      val methods = rejections map (_.supported)
      lazy val names = methods map (_.name) mkString ", "

      respondWithHeader(Allow(methods)) {
        options {
          complete(s"Supported methods : $names.")
        } ~
          complete(MethodNotAllowed, s"HTTP method not allowed, supported methods: $names!")
      }
    }.result()

  val appRoute = getFromResource("webapp/index.html")
  val configRoute = path("config") {
    get {
      respondWithHeaders(List(
        `Access-Control-Allow-Origin`.*,
        `Access-Control-Allow-Credentials`(true),
        `Access-Control-Allow-Headers`("Authorization",
          "Content-Type", "X-Requested-With"),
      )) {
        complete(config)
      }
    }
  }

  val simulationRoute = path("simulation" / Segment) { fileName =>
    post {
      respondWithHeaders(List(
        `Access-Control-Allow-Origin`.*,
        `Access-Control-Allow-Credentials`(true),
        `Access-Control-Allow-Headers`("Authorization",
          "Content-Type", "X-Requested-With"),
      )) {
        entity(as[Config]) { c =>
          logger.info(s"Received config: ${c}")
          Simulation.start(1L, c, 100, c.startDate, c.endDate, fileName)
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  val staticRoute = pathPrefix("static")(getFromResourceDirectory("webapp/static"))

  var fromClientSink = Sink
    .foreach((m: Message) => logger.info(s"Message received from client: $m"))

  var pingSource = Source.tick(1 second, 10 second, TextMessage("ping"))

  val websocket = path("websocket" / Segment) { fileName =>
    extractUpgradeToWebSocket { upgrade =>
      def fileSource = {
        val file = Paths.get(s"$fileName.log")
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

      val onDemandDataFlow = Flow[Message]
        .zip(fileSource)
        .wireTap(m => logger.info(s"Message received from client: ${m._1}"))
        .map(_._2)
      complete(upgrade.handleMessages(onDemandDataFlow))
    }
  }

  val route: Route = websocket ~ simulationRoute ~ configRoute ~ staticRoute ~ appRoute

}

