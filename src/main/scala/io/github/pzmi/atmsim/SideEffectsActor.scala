package io.github.pzmi.atmsim

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props}

import scala.collection.mutable

object SideEffectsActor {
  def props(): Props = {
    Props(new SideEffectsActor())
  }
}

class SideEffectsActor() extends Actor with ActorLogging {
  implicit private val queueOrdering: Ordering[SideEffectEvent] = Ordering.by(e => e.when)
  private val eventsQueue = mutable.PriorityQueue()

  override def receive: Receive = {
    case Withdrawal(time, _, _, _, _) => eventsQueue
      .headOption
      .flatMap(e => if (time.isAfter(e.when)) Some(eventsQueue.dequeue()) else Option.empty)
      .foreach(e => log.info(s"Event from side effect $e"))
    case e: OutOfMoney => eventsQueue.enqueue(SideEffectEvent(e.time.plusSeconds(600), e))
  }
}

case class SideEffectEvent(when: Instant, event: Event)