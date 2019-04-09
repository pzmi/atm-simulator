package io.github.pzmi.atmsim

import java.time.{Instant, LocalDateTime, ZoneOffset}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

object GeneratorActor {
  private val TimeZone = ZoneOffset.UTC

  def props(atms: Array[ActorRef],
            numberOfEvents: Int,
            startDate: LocalDateTime,
            endDate: LocalDateTime,
            outputActor: ActorRef)
           (implicit materializer: Materializer, executionContext: ExecutionContext): Props = {
    val sourceStream = eventsGeneratorStreamWith(atms, numberOfEvents, startDate, endDate)

    Props(new GeneratorActor(atms, sourceStream, outputActor))
  }

  private def eventsGeneratorStreamWith(atms: Array[ActorRef],
                                        eventsPerHour: Int,
                                        startDate: LocalDateTime,
                                        endDate: LocalDateTime,
                                       ): Source[Instant, NotUsed] = {

    val timeIterator: Iterator[Instant] = Iterator.iterate(startDate)(d => d.plusHours(1))
      .takeWhile(i => !i.isAfter(endDate))
      .map(d => d.toInstant(TimeZone))
    Source.fromIterator(() => timeIterator)
      .flatMapConcat(i => Source(0 until eventsPerHour).map(_ => i))
  }
}

class GeneratorActor(private val atms: Array[ActorRef],
                     private val sourceStream: Source[Instant, NotUsed],
                     private val outputActor: ActorRef)
                    (implicit private val materializer: Materializer,
                     implicit private val executionContext: ExecutionContext) extends Actor with ActorLogging {
  override def receive: Receive = {
    case _: StartGeneration => start(outputActor, sourceStream)
    case _: Complete => log.info("Completed generation")
  }

  private def start(outputActor: ActorRef, stream: Source[Instant, NotUsed])(implicit materializer: Materializer,
                                                                             executionContext: ExecutionContext): Unit = {
    val generatorStartTime = System.nanoTime()
    log.info("Starting simulation")
    stream
      .mapAsync(Runtime.getRuntime.availableProcessors()) {
        i: Instant => sendMessage(i, 10000, atms(Random.nextInt(1000) % atms.length))
      }.runWith(Sink.ignore)
      .onComplete(_ => outputActor ! Complete(generatorStartTime))
  }

  private def sendMessage(timestamp: Instant, amount: Int, destination: ActorRef) = {
    import akka.pattern.ask
    implicit val askTimeout: Timeout = Timeout(30 seconds)

    destination ? Withdrawal(timestamp, amount)
  }
}


case class StartGeneration()