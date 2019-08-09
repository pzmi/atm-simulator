package io.github.pzmi.atmsim

import java.time.Instant

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.json4s.{CustomSerializer, JString}

import scala.concurrent.Await
import scala.concurrent.duration._


object AtmActor {
  def props(output: ActorRef, startingBalance: Int = 10000): Props = Props(new AtmActor(output, startingBalance))
}

class AtmActor(output: ActorRef, startingBalance: Int) extends Actor with ActorLogging {

  override def receive: Receive = operational(startingBalance)

  private def outOfMoney: Receive = {
    case w: Withdrawal => sendToOutputAndAck(0, w, OutOfMoneyState)
      sendToOutputAndAck(0, OutOfMoney(w.time), OutOfMoneyState)
    case r: Refill =>
      handleOperational(0, r)
  }

  private def operational(currentBalance: Int): Receive = {
    case w: Withdrawal => handleOperational(currentBalance, w)
    case r: Refill =>
      log.debug(s"Received refill $r")
      handleOperational(currentBalance, r)
    case e => log.debug(s"Received invalid event $e from ${sender()}")
  }

  private def handleOperational(currentBalance: Int, event: Event): Unit = {
    log.debug("Received event: [{}]", event)
    val newBalance: Int = calculateBalance(currentBalance, event)
    sendToOutputAndAck(newBalance, event, OperationalState)
  }

  private def calculateBalance(currentBalance: Int, e: Event): Int =
    e match {
      case Withdrawal(_, amount, _, _, _,_) => calculateBalance(currentBalance, -amount, e)
      case Refill(_, _, _, amount, _,_) => calculateBalance(0, amount, e)
      case _ => throw new IllegalArgumentException(s"Event not handled: $e")
    }

  private def calculateBalance(currentBalance: Int, amount: Int, e: Event): Int =
    currentBalance + amount match {
      case newBalance if newBalance < 0 => sendToOutputAndAck(currentBalance, NotEnoughMoney(e.time), OperationalState)
        currentBalance
      case newBalance if newBalance == 0 => sendToOutputAndAck(newBalance, OutOfMoney(e.time), OutOfMoneyState)
        context.become(outOfMoney)
        newBalance
      case newBalance if newBalance > 0 =>
        context.become(operational(newBalance))
        newBalance
    }

  private def sendToOutputAndAck(newBalance: Int, event: Event, state: AtmState): Unit = {
    sendToOutputAndAck(updateBalanceAndAtm(newBalance, event, state))

    def sendToOutputAndAck(e: Event): Unit = {
      implicit val timeout: Timeout = Timeout(30 seconds)
//      Await.result(output ? e, 30 seconds)
//      sender() ! Done
      output ! e
      sender() ! Done
    }

    def updateBalanceAndAtm(newBalance: Int, event: Event, state: AtmState) = event match {
      case w: Withdrawal => w.copy(balance = newBalance, atm = name, state = state)
      case o: OutOfMoney => o.copy(atm = name)
      case n: NotEnoughMoney => n.copy(atm = name)
      case r: Refill => r.copy(balance = newBalance, atm = name, state = state)
      case _: Event => throw new IllegalStateException("Event is not a correct event")
    }
  }

  private def name = self.path.name
}

sealed class Event(val time: Instant, val eventType: String, val atm: String)

case class Withdrawal(override val time: Instant,
                      amount: Int,
                      override val atm: String = "unknown",
                      override val eventType: String = "withdrawal",
                      balance: Int = 0,
                      state: AtmState = OperationalState) extends Event(time, eventType, atm)

case class OutOfMoney(override val time: Instant,
                      override val atm: String = "unknown",
                      override val eventType: String = "out-of-money") extends Event(time, eventType, atm)

case class NotEnoughMoney(override val time: Instant,
                          override val atm: String = "unknown",
                          override val eventType: String = "not-enough-money") extends Event(time, eventType, atm)

case class Refill(override val time: Instant,
                  override val atm: String = "unknown",
                  override val eventType: String = "refill",
                  amount: Int,
                  balance: Int = 0,
                  state: AtmState = OperationalState) extends Event(time, eventType, atm)

case class PreparedToStart(override val time: Instant,
                           override val atm: String) extends Event(time, "prepared-to-start", atm)

sealed abstract class AtmState
case object OperationalState extends AtmState
case object OutOfMoneyState extends AtmState

object AtmStateSerializer extends CustomSerializer[AtmState](_ => ( {
  case JString("Operational") => OperationalState
  case JString("OutOfMoney") => OutOfMoneyState
  case _ => throw new IllegalArgumentException("No such distribution supported")

}, {
  case OperationalState => JString("Operational")
  case OutOfMoneyState => JString("OutOfMoney")
}))