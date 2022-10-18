package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

trait Config {
  def numberOfNodes: Int
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
  def averageNumberOfActiveNodes: Double //called in go-spacemesh: expected-commity-size (defaults to 800)
  def averageNumberOfLeaders: Double //called in go-spacemesh: expected-leaders (defaults to 5)
  def maxNumberOfIterations: Int
  def manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]]

  //this is the actual number of faulty nodes in this simulation
  val actualNumberOfFaultyNodes: Int = math.floor(numberOfNodes * maxFractionOfMaliciousNodes).toInt

  //this is the number "f" used in the protocol spec
  //i.e. the maximal number of faulty nodes Hare protocol can tolerate
  val faultyNodesTolerance: Int =
    if (numberOfNodes % 2 == 0)
      numberOfNodes / 2 - 1
    else
      numberOfNodes / 2
}
