package com.selfdualbrain.trix.experiments.cfg

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}
import com.selfdualbrain.trix.turns_based_engine.Config

object MediumBlockchainRealistic extends Config {
  override val maxFractionOfFaultyNodes: Double = 0
  override val fractionOfDeafNodesAmongFaulty: Double = 0
  override val inputSetSizeRange: (Int, Int) = (15, 20)
  override val marblesRangeForHonestNodes: Int = 20
  override val isNetworkReliable: Boolean = false
  override val probabilityOfAMessageGettingLost: Double = 0.4
  override val numberOfNodes: Int = 100
  override val averageNumberOfActiveNodes: Double = 15
  override val averageNumberOfLeaders: Double = 3
  override val eligibilityRngSeed: Long = 42
  override val rngAlgorithm: String = "jdk-std"
  override val nodeDecisionsRngSeed: Long = 102
  override val msgDeliveryRngSeed: Long = 299792451
  override val initialSizeOfInboxBuffer: Int = 100
  override val inputSetsGeneratorSeed: Long = 42
  override val manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]] = None
  override val enforceFixedNumberOfActiveNodes: Boolean = true
  override val zombieIterationsLimit: NodeId = 3
}
