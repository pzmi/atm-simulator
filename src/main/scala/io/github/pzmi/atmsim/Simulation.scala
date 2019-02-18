package io.github.pzmi.atmsim

import java.time.Instant

import akka.actor.ActorRef
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import io.github.pzmi.atmsim.Application.system

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.Random

object Simulation extends StrictLogging{

  def start(randomSeed: Long, numberOfAtms: Int, numberOfEvents: Int)(implicit materializer: Materializer,
                                                                      executionContext: ExecutionContext): Unit = {
    Random.setSeed(randomSeed)

    val (outputActor, atms) = prepareActors(numberOfAtms)
    val stream = eventsGeneratorStreamWith(atms, numberOfEvents)

    start(outputActor, stream)
    sys.addShutdownHook({
      logger.info("Shutting down")
      outputActor ! Complete
      Await.result(system.terminate(), 10 seconds)
    })
  }

  private def prepareActors(numberOfAtms: Int)(implicit materializer: Materializer) = {
    val outputActor = system.actorOf(OutputActor.props(), "output")
    val atms: Array[ActorRef] = prepareAtms(numberOfAtms, outputActor)
    (outputActor, atms)
  }

  private def prepareAtms(numberOfAtms: Int, outputActor: ActorRef) = {
    (1 to numberOfAtms)
      .map(n => system.actorOf(AtmActor.props(outputActor), s"atm-${n.toString}")).toArray
  }

  private def start(outputActor: ActorRef, stream: Source[Any, Any])(implicit materializer: Materializer,
                                                                     executionContext: ExecutionContext): Unit = {
    val startTime = Instant.now()
    logger.info("Starting simulation")
    stream
      .runWith(Sink.ignore)
      .onComplete(_ => outputActor ! Complete(startTime))
  }

  private def eventsGeneratorStreamWith(atms: Array[ActorRef], numberOfEvents: Int): Source[Any, Any] =
    Source(0 to numberOfEvents)
      .map(_ => Random.nextInt(10000))
      .mapAsync(Runtime.getRuntime.availableProcessors()) {
        n: Int => sendMessage(atms, n)
      }

  private def sendMessage(atms: Array[ActorRef], n: Int) = {
    import akka.pattern.ask
    implicit val askTimeout: Timeout = Timeout(30 seconds)

    atms(n % atms.length) ? Withdrawal(Instant.now(), n)
  }
}
