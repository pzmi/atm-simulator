package io.github.pzmi.atmsim

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}

import scala.annotation.tailrec
import scala.collection.mutable

object SideEffectsActor {
  def props(config: Config): Props = {
    val default = config.default
    val atmToConfig: Map[String, SideEffectConfig] = config.atms.map { a =>
      a.name -> SideEffectConfig(a.refillAmount.getOrElse(config.default.refillAmount),
        a.refillDelayHours.getOrElse(default.refillDelayHours))
    }.toMap
    Props(new SideEffectsActor(atmToConfig))
  }
}

case class SideEffectConfig(refillAmount: Int, refillDelayHours: Int)

class SideEffectsActor(private val atmToConfig: Map[String, SideEffectConfig]) extends Actor with ActorLogging {
  implicit private val queueOrdering: Ordering[SideEffectEvent] = Ordering.by(e => e.when)
  private val eventsQueue = mutable.PriorityQueue()

  override def receive: Receive = {
    case e: OutOfMoney =>
      resp { _ =>
        eventsQueue.enqueue(
          SideEffectEvent(e.time.plusSeconds(TimeUnit.HOURS.toSeconds(atmToConfig(e.atm).refillDelayHours)), e))
      }

    case TimePassed(t) => resp { _ =>
      drainDueEvents()

      @tailrec
      def drainDueEvents() {
        if (eventsQueue.nonEmpty) {
          val e = eventsQueue.dequeue()
          if (t.isAfter(e.when)) {
            context.actorSelection(s"/user/${e.event.atm}") ! Refill(t, amount = atmToConfig(e.event.atm).refillAmount)
            drainDueEvents()
          } else {
            eventsQueue.enqueue(e)
          }
        }
      }
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