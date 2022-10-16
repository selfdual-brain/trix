package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

trait Config {
  def eligibilityRngMasterSeed: Long
  def inputSetsGeneratorSeed: Long
  def msgDeliveryRngSeed: Long
  def maxFractionOfMaliciousNodes: Double
  def fractionOfDeafNodesAmongMalicious: Double
  def inputSetSizeRange: (Int, Int)
  def marblesRangeForHonestNodes: Int
  def initialSizeOfInboxBuffer: Int
  def isNetworkReliable: Boolean
  def probabilityOfAMessageGettingLost: Double
  def numberOfNodes: Int
  def averageNumberOfActiveNodes: Double
  def averageNumberOfLeaders: Double
  def maxNumberOfIterations: Int
  def manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]]

  val numberOfMaliciousNodes: Int = math.floor(numberOfNodes * maxFractionOfMaliciousNodes).toInt
}
