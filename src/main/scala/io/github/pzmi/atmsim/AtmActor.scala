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

  override def receive: Receive = onMessage(startingBalance)

  private def onMessage(currentBalance: Int): Receive = {
    case w: Withdrawal =>
      val newBalance = currentBalance - w.amount
      sendToOutputAndAck(newBalance, w)
      context.become(onMessage(newBalance))

    case e: Event =>
      sendToOutputAndAck(e)
  }

  private def sendToOutputAndAck(e: Event): Unit = {
    implicit val timeout: Timeout = Timeout(30 seconds)
    Await.result(output ? e, 30 seconds)
    sender() ! Done
  }

  private def sendToOutputAndAck(currentBalance: Int, w: Withdrawal): Unit = {
    sendToOutputAndAck(updateBalanceAndAtm(currentBalance, w))
  }

  private def updateBalanceAndAtm(currentBalance: Int, w: Withdrawal) = {
    w.copy(balance = currentBalance, atm = self.path.name)
  }
}

sealed class Event(val time: Instant, val eventType: String, val atm: String, val balance: Int)

case class Withdrawal(override val time: Instant,
                      amount: Int,
                      override val atm: String = "unknown",
                      override val balance: Int = 0) extends Event(time, "withdrawal", atm, balance)
