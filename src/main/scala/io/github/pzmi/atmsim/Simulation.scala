package io.github.pzmi.atmsim

import java.time.Instant
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps

object Simulation extends StrictLogging {

  def start(randomSeed: Long,
            config: Config,
            eventsPerHour: Int,
            startDate: Instant,
            endDate: Instant,
           fileName: String)(implicit materializer: Materializer,
                                      executionContext: ExecutionContext): Unit = {
    implicit val actorSystem: ActorSystem = ActorSystem(s"$fileName-${UUID.randomUUID().toString}")
    val (outputActor, sideEffects, atms) = prepareActors(config, fileName, startDate)
    val generatorActor = actorSystem.actorOf(
      GeneratorActor.props(atms, eventsPerHour, startDate, endDate, outputActor, sideEffects, config, randomSeed), "generator")
    generatorActor ! StartGeneration()

    sys.addShutdownHook({
      logger.info("Shutting down")
      outputActor ! Complete
      Await.result(actorSystem.terminate(), 10 seconds)
    })
  }

  private def prepareActors(config: Config, fileName: String, startDate: Instant)(implicit materializer: Materializer, actorSystem: ActorSystem) = {
    val sideEffects = actorSystem.actorOf(SideEffectsActor.props(config, startDate), "side-effects")
    val outputActor = actorSystem.actorOf(OutputActor.props(sideEffects, fileName), "output")
    val atmActors: Array[ActorRef] = prepareAtms(config, outputActor)
    (outputActor, sideEffects, atmActors)
  }

  private def prepareAtms(config: Config, outputActor: ActorRef)(implicit actorSystem: ActorSystem) = {
    config.atms
      .map(atm => actorSystem.actorOf(
        AtmActor.props(outputActor,
          atm.refillAmount.getOrElse(config.default.refillAmount)),
        atm.name))
      .toArray
  }
}
