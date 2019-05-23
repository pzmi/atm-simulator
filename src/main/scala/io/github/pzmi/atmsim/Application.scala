package io.github.pzmi.atmsim

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes.MethodNotAllowed
import akka.http.scaladsl.model.headers.{Allow, _}
import akka.http.scaladsl.server.Directives.{complete, options, _}
import akka.http.scaladsl.server.{MethodRejection, RejectionHandler}
import com.typesafe.scalalogging.StrictLogging

import scala.language.postfixOps

object Application extends App with ActorModule with ServerModule with StrictLogging {

  implicit def rejectionHandler: RejectionHandler =
    RejectionHandler.newBuilder().handleAll[MethodRejection] { rejections =>
      val methods = rejections map (_.supported)
      lazy val names = methods map (_.name) mkString ", "

      respondWithHeaders(List(Allow(methods),
        `Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE),
        `Access-Control-Allow-Origin`.*,
        `Access-Control-Allow-Credentials`(true),
        `Access-Control-Allow-Headers`("Authorization",
          "Content-Type", "X-Requested-With"))) {
        options {
          complete(s"Supported methods : $names.")
        } ~
          complete(MethodNotAllowed, s"HTTP method not allowed, supported methods: $names!")
      }
    }.result()

  private val host = "localhost"
  private val port = 8080
  val bindingFuture = Http().bindAndHandle(server.route, host, port)

  logger.info(s"Starting server on http://$host:$port")

  sys.addShutdownHook({
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  })
}