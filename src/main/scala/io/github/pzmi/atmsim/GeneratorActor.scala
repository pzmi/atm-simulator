package io.github.pzmi.atmsim

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.ThreadLocalRandom

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

object GeneratorActor {
  private val TimeZone = ZoneOffset.UTC

  def hourlyDistributions(config: Config) = {
    config.atms.map{ atm =>
      atm.hourly
    }
  }

  def props(atms: Array[ActorRef],
            numberOfEvents: Int,
            startDate: LocalDateTime,
            endDate: LocalDateTime,
            outputActor: ActorRef,
            sideEffectsActor: ActorRef,
            config: Config)
           (implicit materializer: Materializer, executionContext: ExecutionContext): Props = {
    val sourceStream = eventsGeneratorStreamWith(atms, startDate, endDate)


    Props(new GeneratorActor(atms, sourceStream, outputActor, sideEffectsActor, numberOfEvents, config))
  }

  private def eventsGeneratorStreamWith(atms: Array[ActorRef],
                                        startDate: LocalDateTime,
                                        endDate: LocalDateTime,
                                       ): Source[Instant, NotUsed] = {

    val timeIterator: Iterator[Instant] = Iterator.iterate(startDate)(d => d.plusHours(1))
      .takeWhile(i => !i.isAfter(endDate))
      .map(d => d.toInstant(TimeZone))
    Source.fromIterator(() => timeIterator)
  }
}

class GeneratorActor(private val atms: Array[ActorRef],
                     private val sourceStream: Source[Instant, NotUsed],
                     private val outputActor: ActorRef,
                     private val sideEffectsActor: ActorRef,
                     private val eventsPerHour: Int,
                     private val config: Config)
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
      val distribution = config.atms.flatMap {atm =>
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
          val a = ThreadLocalRandom.current().nextInt(1000)
          sendMessage(timestamp, 10000, atms(a % atms.length))
      }

      timeFuture.concat(sendFuture)
    }).mapAsync(Runtime.getRuntime.availableProcessors())(identity)
      .runWith(Sink.ignore)
      .onComplete(_ => outputActor ! Complete(generatorStartTime))
  }

  private def sendMessage(timestamp: Instant, amount: Int, destination: ActorRef) = {
    import akka.pattern.ask
    implicit val askTimeout: Timeout = Timeout(30 seconds)

    destination ? Withdrawal(timestamp, amount)
  }
}


case class StartGeneration()

case class TimePassed(time: Instant)