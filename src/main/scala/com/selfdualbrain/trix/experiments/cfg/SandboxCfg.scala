package com.selfdualbrain.trix.experiments.cfg

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}
import com.selfdualbrain.trix.turns_based_engine.Config

object SandboxCfg extends Config {
  override val maxFractionOfFaultyNodes: Double = 0
  override val fractionOfDeafNodesAmongFaulty: Double = 0

  override val inputSetSizeRange: (Int, Int) = (5, 15)
  override val marblesRangeForHonestNodes: Int = 15

  override val isNetworkReliable: Boolean = true
  override val probabilityOfAMessageGettingLost: Double = 0

  override val numberOfNodes: Int = 10
  override val averageNumberOfActiveNodes: Double = 5
  override val averageNumberOfLeaders: Double = 1

  override val eligibilityRngMasterSeed: Long = 42
  override val nodeDecisionsRngSeed: Long = 101
  override val msgDeliveryRngSeed: Long = 299792458
  override val inputSetsGeneratorSeed: Long = 43

  override val initialSizeOfInboxBuffer: Int = 10
  override val manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]] = None
}
