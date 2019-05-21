package io.github.pzmi.atmsim

import java.nio.file.Paths
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.time.Instant
import java.util.concurrent.{Executors, TimeUnit}

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.scaladsl.{FileIO, Source, SourceQueueWithComplete}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import org.json4s
import org.json4s._
import org.json4s.jackson.Serialization.write

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, TimeoutException}

object OutputActor {

  def props(sideEffects: ActorRef, fileName: String)(implicit materializer: Materializer): Props = {
    implicit val formats: AnyRef with Formats = DefaultFormats + InstantSerializer

    val output = FileIO.toPath(Paths.get(s"$fileName.log"), Set(WRITE, TRUNCATE_EXISTING, CREATE))

    val queue = Source.queue[Event](10000000, OverflowStrategy.backpressure)
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
      try {
        Await.result(sideEffects ? e, 10 seconds)
      } catch {
        case ex: TimeoutException => log.error(s"Timed out on message: ${e}")
      }

      Await.result(queue.offer(e), 10 seconds)
      sender() ! Done
    case Complete(startTimestamp) =>
      import scala.concurrent.duration._
      log.info("Completing")
      queue.complete()
      Await.result(queue.watchCompletion(), 30 seconds)
      val processingTime = System.nanoTime() - startTimestamp
      log.info("Processing time {} nanoseconds = {} seconds",
        processingTime,
        TimeUnit.NANOSECONDS.toSeconds(processingTime))
      log.info("Done")
  }

}

case class Complete(startTime: Long)

object InstantSerializer extends CustomSerializer[Instant](_ => ( {
  case JString(str) =>
    Instant.parse(str)
  case JInt(millis) =>
    Instant.ofEpochMilli(millis.longValue())
}, {
  case instant: Instant => JString(instant.toEpochMilli.toString)
  case instant: Instant => JInt(instant.toEpochMilli)
}))
