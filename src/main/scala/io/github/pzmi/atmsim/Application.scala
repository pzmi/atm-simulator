package io.github.pzmi.atmsim

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.StrictLogging

object Application extends App with ActorModule with ServerModule with StrictLogging {
  Simulation.start(1L, 1000, 1000000)

  val appRoute = pathPrefix("app")(getFromResource("index.html"))
  val staticRoute = pathPrefix("app/static")(getFromResourceDirectory("static"))

  val bindingFuture = Http().bindAndHandle(appRoute ~ staticRoute, "localhost", 8080)

  sys.addShutdownHook({
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  })
}
