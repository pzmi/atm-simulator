package io.github.pzmi.atmsim

import java.time.LocalDateTime

import akka.actor.ActorRef
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import io.github.pzmi.atmsim.Application.system

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.Random

object Simulation extends StrictLogging {

  def start(randomSeed: Long,
            config: Config,
            numberOfEvents: Int,
            startDate: LocalDateTime,
            endDate: LocalDateTime)(implicit materializer: Materializer,
                                      executionContext: ExecutionContext): Unit = {
    Random.setSeed(randomSeed)

    val (outputActor, sideEffects, atms) = prepareActors(config)
    val generatorActor = system.actorOf(
      GeneratorActor.props(atms, numberOfEvents, startDate, endDate, outputActor, sideEffects, config), "generator")
    generatorActor ! StartGeneration()

    sys.addShutdownHook({
      logger.info("Shutting down")
      outputActor ! Complete
      Await.result(system.terminate(), 10 seconds)
    })
  }

  private def prepareActors(config: Config)(implicit materializer: Materializer) = {
    val sideEffects = system.actorOf(SideEffectsActor.props(), "side-effects")
    val outputActor = system.actorOf(OutputActor.props(sideEffects), "output")
    val atmActors: Array[ActorRef] = prepareAtms(config, outputActor)
    (outputActor, sideEffects, atmActors)
  }

  private def prepareAtms(config: Config, outputActor: ActorRef) = {
    config.atms
      .map(atm => system.actorOf(
        AtmActor.props(outputActor,
          atm.startingAmount.getOrElse(config.default.amount)),
        s"atm-${atm.name}"))
      .toArray
  }
}
