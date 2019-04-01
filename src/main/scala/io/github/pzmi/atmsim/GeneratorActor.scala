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
  def props(atms: Array[ActorRef],
            numberOfEvents: Int,
            startDate: LocalDateTime,
            outputActor: ActorRef)
           (implicit materializer: Materializer, executionContext: ExecutionContext): Props = {
    val sourceStream = eventsGeneratorStreamWith(atms, numberOfEvents, startDate)

    Props(new GeneratorActor(atms, sourceStream, outputActor))
  }

  private def eventsGeneratorStreamWith(atms: Array[ActorRef],
                                        numberOfEvents: Int,
                                        startDate: LocalDateTime,
                                       ): Source[Instant, NotUsed] =
    Source(0 to numberOfEvents)
      .scan(startDate.toInstant(ZoneOffset.UTC))((acc, _) => acc.plusSeconds(10))
}

class GeneratorActor(private val atms: Array[ActorRef],
                     private val sourceStream: Source[Instant, NotUsed],
                     private val outputActor: ActorRef)
                    (implicit private val materializer: Materializer,
                     implicit private val executionContext: ExecutionContext) extends Actor with ActorLogging {
  // TODO: move generation to actor so when simulation stream ends, messages still can be send and generator can respond to ASK pattern
  // FIXME: when atm actor sends act and stream has already ended, actor fails to ack
  override def receive: Receive = {
    case _: StartGeneration => start(outputActor, sourceStream)
    case _: Complete =>
  }

  private def start(outputActor: ActorRef, stream: Source[Instant, NotUsed])(implicit materializer: Materializer,
                                                                             executionContext: ExecutionContext): Unit = {
    val startTime = Instant.now()
    log.info("Starting simulation")
    stream.mapAsync(Runtime.getRuntime.availableProcessors()) {
      i: Instant => sendMessage(i, 10000, atms(Random.nextInt(1000) % atms.length))
    }.runWith(Sink.ignore)
      .onComplete(_ => outputActor ! Complete(startTime))
  }

  private def sendMessage(timestamp: Instant, amount: Int, destination: ActorRef) = {
    import akka.pattern.ask
    implicit val askTimeout: Timeout = Timeout(30 seconds)

    destination ? Withdrawal(timestamp, amount)
  }
}

case class StartGeneration()