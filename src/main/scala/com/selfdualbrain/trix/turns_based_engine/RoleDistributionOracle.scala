package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.NodeId
import org.apache.commons.math3.random.MersenneTwister

trait RoleDistributionOracle {
  def isNodeActive(validator: NodeId): Boolean
}

//we assume node ids form a 0-based interval
class CachingRoleDistributionOracle(numberOfNodes: Int, electedSubsetAverageSize: Double, rngSeed: Long) extends RoleDistributionOracle {
  private val fractionToBeElected: Double = electedSubsetAverageSize / numberOfNodes
  private val cache: Array[Boolean] = new Array[Boolean](numberOfNodes)

  runElection()

  override def isNodeActive(validator: NodeId): Boolean = cache(validator)

  private def runElection(): Unit = {
    val rng = new MersenneTwister(rngSeed)
    for (i <- 0 until numberOfNodes) {
      cache(i) = rng.nextDouble() < fractionToBeElected
    }
  }

}
