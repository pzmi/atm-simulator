package io.github.pzmi.atmsim

import java.time.Instant
import java.util.concurrent.TimeUnit

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}

import scala.annotation.tailrec
import scala.collection.mutable

object SideEffectsActor {
  def props(config: Config, startDate: Instant): Props = {
    val default = config.default
    val atmToConfig: Map[String, SideEffectConfig] = config.atms.map { a =>
      a.name -> SideEffectConfig(
        a.refillAmount.getOrElse(config.default.refillAmount),
        a.refillDelayHours.getOrElse(default.refillDelayHours),
        a.scheduledRefillInterval.getOrElse(default.scheduledRefillInterval))
    }.toMap
    Props(new SideEffectsActor(atmToConfig, startDate))
  }
}

case class SideEffectConfig(refillAmount: Int,
                            refillDelayHours: Int,
                            scheduledRefillInterval: Int)

class SideEffectsActor(private val atmToConfig: Map[String, SideEffectConfig],
                       private val startTime: Instant) extends Actor with ActorLogging {
  implicit private val queueOrdering: Ordering[SideEffectEvent] = Ordering.by(e => e.when)
  private val eventsQueue = mutable.PriorityQueue()

  override def preStart(): Unit = {
    atmToConfig.foreach { case (atm, _) =>
      self ! PreparedToStart(startTime, atm)
    }
  }

  override def postRestart(reason: Throwable): Unit = ()

  override def receive: Receive = {
    case e: PreparedToStart =>
      val scheduledRefillDelay: Long = scheduledRefillDelayFrom(e)
      val sideEffectScheduleTime = startTime.plusSeconds(scheduledRefillDelay)
      eventsQueue.enqueue(SideEffectEvent(sideEffectScheduleTime, e))

//    case e: OutOfMoney =>
//      resp { _ =>
//        eventsQueue.enqueue(
//          SideEffectEvent(e.time.plusSeconds(TimeUnit.HOURS.toSeconds(atmToConfig(e.atm).refillDelayHours)), e))
//      }

    case TimePassed(timeThatPassed) => resp { _ =>
      log.debug("Time passed")
      drainDueEvents()

      @tailrec
      def drainDueEvents() {
        if (eventsQueue.nonEmpty) {
          val eventFromQueue = eventsQueue.dequeue()
          if (!timeThatPassed.isBefore(eventFromQueue.when)) {
            log.info("Draining")
            context.actorSelection(s"/user/${eventFromQueue.event.atm}") !
              Refill(timeThatPassed, amount = atmToConfig(eventFromQueue.event.atm).refillAmount)
            val nextRefill = eventFromQueue.when.plusSeconds(scheduledRefillDelayFrom(eventFromQueue.event))
            eventsQueue.enqueue(eventFromQueue.copy(when = nextRefill))
            drainDueEvents()
          } else {
            log.debug(s"Enqueue $eventFromQueue on $timeThatPassed")
            eventsQueue.enqueue(eventFromQueue)
          }
        }
      }
    }
    case _: Event => resp { _ => }
  }

  private def scheduledRefillDelayFrom(e: Event) = {
    val atmConfig = atmToConfig(e.atm)
    val scheduledRefillDelay = TimeUnit.HOURS.toSeconds(atmConfig.scheduledRefillInterval)
    scheduledRefillDelay
  }

  private def resp[T](toResponseAfter: Any => T): T = {
    val result = toResponseAfter()
    sender() ! Done
    result
  }
}

case class SideEffectEvent(when: Instant, event: Event)