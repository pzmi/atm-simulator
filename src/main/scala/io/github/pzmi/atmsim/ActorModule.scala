package io.github.pzmi.atmsim

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContext

trait ActorModule {
  implicit val system: ActorSystem = ActorSystem()

  implicit val dispatcher: ExecutionContext = system.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()
}
