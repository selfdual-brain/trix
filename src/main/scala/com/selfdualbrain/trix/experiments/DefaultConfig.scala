package com.selfdualbrain.trix.experiments

import com.selfdualbrain.trix.turns_based_engine.Config

object DefaultConfig extends Config {
  override val maxFractionOfMaliciousNodes: Double = 0.4
  override val fractionOfDeafNodesAmongMalicious: Double = 0.1
  override val inputSetSizeRange: (Int, Int) = (3, 10)
  override val marblesRangeForHonestNodes: Int = 20
  override val isNetworkReliable: Boolean = true
  override val probabilityOfAMessageGettingLost: Double = 0
  override val numberOfNodes: Int = 100
  override val averageNumberOfActiveNodes: Double = 10
  override val averageNumberOfLeaders: Double = 1
  override val maxNumberOfIterations: Int = 500
}
