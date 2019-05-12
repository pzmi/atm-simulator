package io.github.pzmi.atmsim

import java.nio.file.Paths
import java.time.{LocalDate, LocalDateTime, LocalTime}

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{FileIO, Flow, Framing, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import org.json4s.jackson.Serialization
import org.json4s.{CustomSerializer, DefaultFormats, Formats, JString}

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.http.scaladsl.model.headers._

object Application extends App with ActorModule with ServerModule with StrictLogging {
  implicit val formats: AnyRef with Formats = DefaultFormats + InstantSerializer + DistributionSerializer

  private val configString: String = scala.io.Source.fromResource("config.json").getLines.mkString("\n")
  val config: Config = Serialization.read[Config](configString)

  val appRoute = getFromResource("webapp/index.html")
  val configRoute = path("config") {
    get {
      respondWithHeaders(List(
        `Access-Control-Allow-Origin`.*,
        `Access-Control-Allow-Credentials`(true),
        `Access-Control-Allow-Headers`("Authorization",
          "Content-Type", "X-Requested-With")
      )) {
        complete(HttpEntity(ContentTypes.`application/json`, Serialization.write[Config](config)))
      }
    }
  }

  val simulationRoute = path("simulation" / Segment) { fileName =>
    post {
      val startDate = LocalDateTime.of(LocalDate.of(2019, 3, 3), LocalTime.of(11, 11))
      val days = 10
      val hoursPerDay = 24
      Simulation.start(1L, config, 100, startDate, startDate.plusHours(hoursPerDay * days), fileName)
      complete(StatusCodes.NoContent)
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

  private val host = "localhost"
  private val port = 8080
  val bindingFuture = Http().bindAndHandle(websocket ~ simulationRoute ~ configRoute ~ staticRoute ~ appRoute, host, port)

  logger.info(s"Starting server on http://$host:$port")

  sys.addShutdownHook({
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  })
}

case class Config(default: DefaultProperties, withdrawal: WithdrawalProperties,
                  atms: List[AtmProperties])

case class DefaultProperties(refillAmount: Int, refillDelayHours: Int, load: Int)

case class AtmProperties(name: String, location: List[Double],
                         atmDefaultLoad: Option[Int],
                         refillAmount: Option[Int],
                         refillDelayHours: Option[Int],
                         hourly: Map[String, HourlyProperties] = Map.empty)


case class HourlyProperties(load: Int)

case class WithdrawalProperties(min: Int, max: Int, stddev: Int, mean: Int, distribution: Distribution)

sealed abstract class Distribution
case object Normal extends Distribution
case object Gaussian extends Distribution

object DistributionSerializer extends CustomSerializer[Distribution](_ => ( {
  case JString("Normal") => Normal
  case JString("Gaussian") => Gaussian
  case _ => throw new IllegalArgumentException("No such distribution supported")

}, {
  case Normal => JString("Normal")
  case Gaussian => JString("Gaussian")
}))
