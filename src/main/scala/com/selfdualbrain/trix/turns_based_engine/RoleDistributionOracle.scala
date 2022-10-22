package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.NodeId

trait RoleDistributionOracle {
  def isNodeActive(validator: NodeId): Boolean
}

//we assume node ids form a 0-based interval
class CachingRoleDistributionOracle(
                                     numberOfNodes: Int,
                                     electedSubsetAverageSize: Double,
                                     enforceFixedNumberOfActiveNodes: Boolean,
                                     rng: RandomNumberGenerator) extends RoleDistributionOracle {

  private val fractionToBeElected: Double = electedSubsetAverageSize / numberOfNodes
  private val cache: Array[Boolean] = new Array[Boolean](numberOfNodes)

  runElection()

  override def isNodeActive(validator: NodeId): Boolean = cache(validator)

  private def runElection(): Unit = {
    if (enforceFixedNumberOfActiveNodes) {
      val selectedSet = IntIntervalSubsetPicker.run(rng, numberOfNodes - 1, electedSubsetAverageSize.toInt)
      for (i <- 0 until numberOfNodes)
        cache(i) = selectedSet.contains(i)
    } else {
      for (i <- 0 until numberOfNodes)
        cache(i) = rng.nextDouble() < fractionToBeElected

    }

  }

}
