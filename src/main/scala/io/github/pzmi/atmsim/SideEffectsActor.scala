package io.github.pzmi.atmsim

import java.time.Instant

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}

import scala.collection.mutable
import scala.concurrent.Await
import akka.pattern.ask
import scala.concurrent.duration._

import akka.util.Timeout

object SideEffectsActor {
  def props(): Props = {
    Props(new SideEffectsActor())
  }
}

class SideEffectsActor() extends Actor with ActorLogging {
  implicit private val queueOrdering: Ordering[SideEffectEvent] = Ordering.by(e => e.when)
  private val eventsQueue = mutable.PriorityQueue()

  override def receive: Receive = {
    case e: OutOfMoney =>
      resp { _ => eventsQueue.enqueue(SideEffectEvent(e.time.plusSeconds(10), e)) }

    case TimePassed(t) => resp { _ =>
      eventsQueue
        .headOption
        .flatMap(e => if (t.isAfter(e.when)) Some(eventsQueue.dequeue()) else Option.empty)
        .map(e => context.actorSelection(s"/user/${e.event.atm}"))
        .foreach(a => a ! Refill(t, amount = 10000))
    }
    case _: Event => resp { _ => }
  }

  private def resp[T](toResponseAfter: Any => T): T = {
    val result = toResponseAfter()
    sender() ! Done
    result
  }
}

case class SideEffectEvent(when: Instant, event: Event)