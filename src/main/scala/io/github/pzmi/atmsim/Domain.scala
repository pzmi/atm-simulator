package io.github.pzmi.atmsim

import java.time.Instant

import org.json4s.{CustomSerializer, JString}

case class Config(startDate: Instant, endDate: Instant, default: DefaultProperties, withdrawal: WithdrawalProperties,
                  atms: List[AtmProperties], eventsPerHour: Int)

case class DefaultProperties(refillAmount: Int, refillDelayHours: Int, load: Int, scheduledRefillInterval: Int)

case class AtmProperties(name: String, location: List[Double],
                         atmDefaultLoad: Option[Int],
                         refillAmount: Option[Int],
                         refillDelayHours: Option[Int],
                         hourly: Map[String, HourlyProperties] = Map.empty,
                         scheduledRefillInterval: Option[Int])


case class HourlyProperties(load: Int)

case class WithdrawalProperties(min: Int, max: Int, stddev: Int, mean: Int, distribution: Distribution)

sealed abstract class Distribution
case object Normal extends Distribution
case object Gaussian extends Distribution

object DistributionSerializer extends CustomSerializer[Distribution](_ => ( {
  case JString("Normal") => Normal
  case JString("Gaussian") => Gaussian
  case _ => throw new IllegalArgumentException("No such distribution supported")

}, {
  case Normal => JString("Normal")
  case Gaussian => JString("Gaussian")
}))
