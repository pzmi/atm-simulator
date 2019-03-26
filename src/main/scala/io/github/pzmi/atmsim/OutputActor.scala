package io.github.pzmi.atmsim

import java.nio.file.Paths
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.time.Instant
import java.util.concurrent.Executors

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.scaladsl.{FileIO, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import org.json4s._
import org.json4s.jackson.Serialization.write

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}

object OutputActor {

  def props(sideEffects: ActorRef)(implicit materializer: Materializer): Props = {
    implicit val formats: AnyRef with Formats = DefaultFormats + InstantSerializer

    val output = FileIO.toPath(Paths.get("output.txt"), Set(WRITE, TRUNCATE_EXISTING, CREATE))

    val queue = Source.queue[Event](1000000, OverflowStrategy.backpressure)
      .groupedWithin(10000, 1 second)
      .flatMapMerge(1, e => Source(e.sortBy(a => a.time)))
      .map(e => write(e))
      .map(json => ByteString(json + System.lineSeparator()))
      .to(output)
      .run()


    Props(new OutputActor(queue, sideEffects))
  }
}

class OutputActor(queue: SourceQueueWithComplete[Event], sideEffects: ActorRef) extends Actor with ActorLogging {
  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  override def receive: Receive = {
    case e: Event =>
      import akka.pattern.ask
      implicit val askTimeout: Timeout = Timeout(30 seconds)
      Await.result(sideEffects ? e, 10 seconds)

      Await.result(queue.offer(e), 10 seconds)
      sender() ! Done
    case c: Complete =>
      import scala.concurrent.duration._
      log.info("Completing")
      queue.complete()
      Await.result(queue.watchCompletion(), 30 seconds)
      val processingTime = java.time.Duration.between(c.startTime, Instant.now())
      log.info("Processing time {}", processingTime)
      log.info("Done")
  }

}

case class Complete(startTime: Instant)

object InstantSerializer extends CustomSerializer[Instant](_ => ( {
  case JString(str) =>
    Instant.parse(str)
}, {
  case instant: Instant => JString(instant.toEpochMilli.toString)
}))