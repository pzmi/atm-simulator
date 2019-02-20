package io.github.pzmi.atmsim

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.StrictLogging

object Application extends App with ActorModule with ServerModule with StrictLogging {
//  Simulation.start(1L, 1000, 1000000)

  val appRoute = getFromResource("index.html")
  val staticRoute = pathPrefix("static")(getFromResourceDirectory("static"))

  private val host = "localhost"
  private val port = 8080
  val bindingFuture = Http().bindAndHandle(staticRoute ~ appRoute, host, port)

  logger.info(s"Starting server on $host:$port")

  sys.addShutdownHook({
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  })
}
