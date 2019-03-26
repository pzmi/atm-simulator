package io.github.pzmi.atmsim

import java.time.{Instant, LocalDateTime, ZoneOffset}

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

object Simulation extends StrictLogging {

  def start(randomSeed: Long,
            numberOfAtms: Int,
            numberOfEvents: Int,
            startDate: LocalDateTime)(implicit materializer: Materializer,
                                      executionContext: ExecutionContext): Unit = {
    Random.setSeed(randomSeed)

    val (outputActor, atms) = prepareActors(numberOfAtms)
    val stream = eventsGeneratorStreamWith(atms, numberOfEvents, startDate)

    start(outputActor, stream)
    sys.addShutdownHook({
      logger.info("Shutting down")
      outputActor ! Complete
      Await.result(system.terminate(), 10 seconds)
    })
  }

  private def prepareActors(numberOfAtms: Int)(implicit materializer: Materializer) = {
    val sideEffects = system.actorOf(SideEffectsActor.props(), "side-effects")
    val outputActor = system.actorOf(OutputActor.props(sideEffects), "output")
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

  private def eventsGeneratorStreamWith(atms: Array[ActorRef],
                                        numberOfEvents: Int,
                                        startDate: LocalDateTime): Source[Any, Any] =
    Source(0 to numberOfEvents)
      .scan(startDate.toInstant(ZoneOffset.UTC))((acc, _) => acc.plusSeconds(10))
      .mapAsync(Runtime.getRuntime.availableProcessors()) {
        i: Instant => sendMessage(atms, i, 10000, atms(Random.nextInt(1000) % atms.length))
      }

  private def sendMessage(atms: Array[ActorRef], timestamp: Instant, amount: Int, destination: ActorRef) = {
    import akka.pattern.ask
    implicit val askTimeout: Timeout = Timeout(30 seconds)

    destination ? Withdrawal(timestamp, amount)
  }
}
