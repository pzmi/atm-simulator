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
  private val startingBalance: Int = 10000

  override def receive: Receive = functional(startingBalance)

  def outOfMoney: Receive = {
    case e: Event => sendToOutputAndAck(0, OutOfMoney(e.time))
  }

  private def functional(currentBalance: Int): Receive = {
    case w: Withdrawal =>
      val newBalance = currentBalance - w.amount

      if (newBalance < 0) {
        val notEnoughMoney = NotEnoughMoney(w.time, balance = currentBalance)
        sendToOutputAndAck(currentBalance, notEnoughMoney)
      } else {
        sendToOutputAndAck(newBalance, w)
        if (newBalance == 0) {
          sendToOutputAndAck(newBalance, OutOfMoney(w.time))
          context.become(outOfMoney)
        } else {
          context.become(functional(newBalance))
        }
      }

    case e: Event =>
      sendToOutputAndAck(e)
  }

  private def sendToOutputAndAck(e: Event): Unit = {
    implicit val timeout: Timeout = Timeout(30 seconds)
    Await.result(output ? e, 30 seconds)
    sender() ! Done
  }

  private def sendToOutputAndAck(newBalance: Int, event: Event): Unit = {
    sendToOutputAndAck(updateBalanceAndAtm(newBalance, event))
  }

  private def updateBalanceAndAtm(newBalance: Int, event: Event) = event match {
    case w: Withdrawal => w.copy(balance = newBalance, atm = name)
    case o: OutOfMoney => o.copy(balance = 0, atm = name)
    case n: NotEnoughMoney => n.copy(atm = name)
    case e: Event => e

  }

  private def name = self.path.name
}

sealed class Event(val time: Instant, val eventType: String, val atm: String, val balance: Int)

case class Withdrawal(override val time: Instant,
                      amount: Int,
                      override val atm: String = "unknown",
                      override val balance: Int = 0,
                      override val eventType: String = "withdrawal") extends Event(time, eventType, atm, balance)

case class OutOfMoney(override val time: Instant,
                      override val atm: String = "unknown",
                      override val balance: Int = 0,
                      override val eventType: String = "out-of-money") extends Event(time, eventType, atm, balance)

case class NotEnoughMoney(override val time: Instant,
                          override val atm: String = "unknown",
                          override val balance: Int = 0,
                          override val eventType: String = "not-enough-money") extends Event(time, eventType, atm, balance)

