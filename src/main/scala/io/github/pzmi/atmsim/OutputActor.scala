package io.github.pzmi.atmsim

import java.nio.file.Paths
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.time.Instant
import java.util.concurrent.Executors

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.{FileIO, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import org.json4s._
import org.json4s.jackson.Serialization.write

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}

object OutputActor {

  def props()(implicit materializer: Materializer): Props = {
    implicit val formats: AnyRef with Formats = DefaultFormats + InstantSerializer

    val output = FileIO.toPath(Paths.get("output.txt"), Set(WRITE, TRUNCATE_EXISTING, CREATE))

    val queue = Source.queue[Event](1000000, OverflowStrategy.backpressure)
      .groupedWithin(10000, 1 second)
      .flatMapMerge(1, e => Source(e.sortBy(a => a.time)))
      .map(e => write(e))
      .map(json => ByteString(json + System.lineSeparator()))
      .to(output)
      .run()


    Props(new OutputActor(queue))
  }
}

class OutputActor(queue: SourceQueueWithComplete[Event]) extends Actor with ActorLogging {
  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  override def receive: Receive = {
    case e: Event =>
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