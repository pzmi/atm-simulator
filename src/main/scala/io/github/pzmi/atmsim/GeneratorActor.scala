package io.github.pzmi.atmsim

import akka.actor.{Actor, ActorLogging}

class GeneratorActor extends Actor with ActorLogging {
  // TODO: move generation to actor so when simulation stream ends, messages still can be send and generator can respond to ASK pattern
  // FIXME: when atm actor sends act and stream has already ended, actor fails to ack
  override def receive: Receive = ???
}
