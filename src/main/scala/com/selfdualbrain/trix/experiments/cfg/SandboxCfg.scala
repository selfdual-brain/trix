package com.selfdualbrain.trix.experiments.cfg

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}
import com.selfdualbrain.trix.turns_based_engine.Config

object SandboxCfg extends Config {
  override val maxFractionOfFaultyNodes: Double = 0
  override val fractionOfDeafNodesAmongFaulty: Double = 0

  override val inputSetSizeRange: (Int, Int) = (15, 20)
  override val marblesRangeForHonestNodes: Int = 20

  override val isNetworkReliable: Boolean = false
  override val probabilityOfAMessageGettingLost: Double = 0.3

  override val numberOfNodes: Int = 10
  override val averageNumberOfActiveNodes: Double = 5
  override val averageNumberOfLeaders: Double = 1

  override val eligibilityRngSeed: Long = 45
  override val nodeDecisionsRngSeed: Long = 46
  override val msgDeliveryRngSeed: Long = 47
  override val inputSetsGeneratorSeed: Long = 48
  override val rngAlgorithm: String = "jdk-std"

  override val initialSizeOfInboxBuffer: Int = 10
  override val manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]] = None
  override val enforceFixedNumberOfActiveNodes: Boolean = true
}
