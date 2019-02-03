package io.github.pzmi.atmsim

import java.time.Instant

import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random


object Application extends App with ActorModule with ServerModule {

  sys.addShutdownHook({
    println("Shutting down")
    outputActor ! Complete
    Await.result(system.terminate(), 10 seconds)
  })

  Random.setSeed(1L)

  val outputActor = system.actorOf(OutputActor.props(), "output")

  val atms = Array(system.actorOf(AtmActor.props(outputActor), "0"), system.actorOf(AtmActor.props(outputActor), "1"))

  Source(0 to 1000000)
    .mapAsync(1) {
      n: Int =>
        import akka.pattern.ask
        implicit val askTimeout: Timeout = Timeout(30 seconds)

        atms(n % 2) ? Event(n, Instant.now())
    }
    .runWith(Sink.ignore)
    .onComplete(_ => outputActor ! Complete)
}
