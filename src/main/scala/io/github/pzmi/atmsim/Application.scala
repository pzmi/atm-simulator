package io.github.pzmi.atmsim

import java.time.Instant

import akka.actor.ActorRef
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random


object Application extends App with ActorModule with ServerModule with StrictLogging {
  sys.addShutdownHook({
    println("Shutting down")
    outputActor ! Complete
    Await.result(system.terminate(), 10 seconds)
  })

  Random.setSeed(1L)
  val outputActor = system.actorOf(OutputActor.props(), "output")

  val numberOfAtms = 1000

  private val atms: Array[ActorRef] = (1 to numberOfAtms)
    .map(n => system.actorOf(AtmActor.props(outputActor), s"atm-${n.toString}")).toArray

  val startTime = Instant.now()

  Source(0 to 1000000)
    .map(_ => Random.nextInt(10000))
    .mapAsync(Runtime.getRuntime.availableProcessors()) {
      n: Int =>
        import akka.pattern.ask
        implicit val askTimeout: Timeout = Timeout(30 seconds)

        atms(n % numberOfAtms) ? Withdrawal(Instant.now(), n)
    }
    .runWith(Sink.ignore)
    .onComplete(_ => outputActor ! Complete(startTime))
}
