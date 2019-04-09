package io.github.pzmi.atmsim

import java.time.Instant

import akka.Done
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
    case Withdrawal(time, _, _, _, _) =>
      resp { _ =>
        eventsQueue
          .headOption
          .flatMap(e => if (time.isAfter(e.when)) Some(eventsQueue.dequeue()) else Option.empty)
          .foreach(e => context.actorSelection(s"../${e.event.atm}") ! Refill(10000))
      }
    case e: OutOfMoney =>
      resp { _ => eventsQueue.enqueue(SideEffectEvent(e.time.plusSeconds(10), e))
      }
  }

  private def resp(toResponseAfter: Any => Any): Unit = {
    toResponseAfter()
    sender() ! Done
  }
}

case class SideEffectEvent(when: Instant, event: Event)