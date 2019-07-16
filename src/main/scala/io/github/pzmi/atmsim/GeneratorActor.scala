package io.github.pzmi.atmsim

import java.time.{Instant, ZoneOffset}
import java.util.Random
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.collection._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object GeneratorActor {
  private val TimeZone = ZoneOffset.UTC

  def hourlyDistributions(config: Config) = {
    config.atms.map { atm =>
      atm.hourly
    }
  }

  def props(atms: Array[ActorRef],
            eventsPerHour: Int,
            startDate: Instant,
            endDate: Instant,
            outputActor: ActorRef,
            sideEffectsActor: ActorRef,
            config: Config,
            randomSeed: Long)
           (implicit materializer: Materializer, executionContext: ExecutionContext): Props = {
    val sourceStream = eventsGeneratorStreamWith(atms, startDate, endDate)


    Props(new GeneratorActor(atms, sourceStream, outputActor, sideEffectsActor, eventsPerHour, config,
      new Random(randomSeed)))
  }

  private def eventsGeneratorStreamWith(atms: Array[ActorRef],
                                        startDate: Instant,
                                        endDate: Instant,
                                       ): Source[Instant, NotUsed] = {

    val timeIterator: Iterator[Instant] =
      Iterator.iterate(startDate)(d => d.plusSeconds(TimeUnit.HOURS.toSeconds(1)))
      .takeWhile(i => !i.isAfter(endDate))
    Source.fromIterator(() => timeIterator)
  }
}

class GeneratorActor(private val atms: Array[ActorRef],
                     private val sourceStream: Source[Instant, NotUsed],
                     private val outputActor: ActorRef,
                     private val sideEffectsActor: ActorRef,
                     private val eventsPerHour: Int,
                     private val config: Config,
                     private val randomGenerator: Random)
                    (implicit private val materializer: Materializer,
                     implicit private val executionContext: ExecutionContext) extends Actor with ActorLogging {

  override def receive: Receive = {
    case _: StartGeneration => start()
    case _: Complete => log.info("Completed generation")
  }

  private def start()(implicit materializer: Materializer, executionContext: ExecutionContext): Unit = {
    val generatorStartTime = System.nanoTime()
    log.info("Starting simulation")
    sourceStream.flatMapConcat(i => {
      val distribution = config.atms.flatMap { atm =>
        val load = atm.hourly.get(i.toEpochMilli.toString)
          .map(_.load)
          .getOrElse(atm.atmDefaultLoad.getOrElse(config.default.load))
        (1 to load).map(_ => atm.name)
      }

      import akka.pattern.ask
      implicit val t = Timeout(1 second)
      val timeFuture: Source[Future[Any], NotUsed] = Source.single(sideEffectsActor ? TimePassed(i))
      val sendFuture: Source[Future[Any], NotUsed] = Source(0 until eventsPerHour).map(_ => i).map {
        timestamp =>
          val random = randomGenerator.nextInt(distribution.length)
          val actorName = distribution(random)
          val selection = context.actorSelection(s"/user/$actorName")
          val wc = config.withdrawal
          val amount = wc.distribution match {
            case Uniform => randomGenerator.nextInt(wc.max - wc.min) + wc.min
            case Gaussian => positiveGaussianRandom(wc.mean, wc.stddev).intValue()
          }

          if (amount > 0) {
            sendMessage(timestamp, amount, selection)
          } else {
            Future.successful(None)
          }
      }

      timeFuture.concat(sendFuture)
    }).mapAsync(Runtime.getRuntime.availableProcessors())(identity)
      .runWith(Sink.ignore)
      .onComplete(_ => outputActor ! Complete(generatorStartTime))
  }

  private def positiveGaussianRandom(mean: Int, stddev: Int) = {
    Math.max(randomGenerator.nextGaussian() * stddev + mean, 0)
  }

  private def sendMessage(timestamp: Instant, amount: Int, destination: ActorSelection) = {
    import akka.pattern.ask
    implicit val askTimeout: Timeout = Timeout(30 seconds)

    log.debug("Sending message to [{}]", destination)

    destination ? Withdrawal(timestamp, amount)
  }
}

case class StartGeneration()

case class TimePassed(time: Instant)