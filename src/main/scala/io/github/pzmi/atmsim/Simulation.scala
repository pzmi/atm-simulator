package io.github.pzmi.atmsim

import java.time.{Instant, LocalDateTime, ZoneOffset}

import akka.NotUsed
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
    val generatorActor = system.actorOf(
      GeneratorActor.props(atms, numberOfAtms, startDate, outputActor), "generator")
    generatorActor ! StartGeneration()

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
}
