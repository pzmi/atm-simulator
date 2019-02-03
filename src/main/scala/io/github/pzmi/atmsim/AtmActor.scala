package io.github.pzmi.atmsim

import java.time.Instant

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._


object AtmActor {
  def props(output: ActorRef): Props = Props(new AtmActor(output))
}

class AtmActor(output: ActorRef) extends Actor with ActorLogging {

  override def receive: Receive = {
    case e: Event =>
      //      log.info(s"Received: $e")
      implicit val timeout: Timeout = Timeout(30 seconds)
      Await.result(output ? e, 30 seconds)
      sender() ! Done
  }
}

case class Event(number: Int, time: Instant)
