package io.github.pzmi.atmsim

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}

object WsActor {
  def props(): Props = {
    Props(new WsActor())
  }
}

class WsActor() extends Actor with ActorLogging {
  override def receive: Receive = {
    case m => log.info(m.toString)
      sender() ! Done
  }
}
