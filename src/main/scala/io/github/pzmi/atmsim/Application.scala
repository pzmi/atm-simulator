package io.github.pzmi.atmsim

import com.typesafe.scalalogging.StrictLogging


object Application extends App with ActorModule with ServerModule with StrictLogging {
  Simulation.start(1L, 1000, 1000000)
}
