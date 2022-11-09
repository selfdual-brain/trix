package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

trait Config {
  def numberOfNodes: Int
  def honestNodeAlgorithm: String = "improved"

  def rngAlgorithm: String
  def eligibilityRngSeed: Long
  def inputSetsGeneratorSeed: Long
  def msgDeliveryRngSeed: Long
  def nodeDecisionsRngSeed: Long

  def enforceFixedNumberOfActiveNodes: Boolean

  def maxFractionOfFaultyNodes: Double
  def fractionOfDeafNodesAmongFaulty: Double
  def inputSetSizeRange: (Int, Int)
  def marblesRangeForHonestNodes: Int
  def initialSizeOfInboxBuffer: Int
  def isNetworkReliable: Boolean
  def probabilityOfAMessageGettingLost: Double
  def averageNumberOfActiveNodes: Double //called in go-spacemesh: expected-committee-size (defaults to 800)
  def averageNumberOfLeaders: Double //called in go-spacemesh: expected-leaders (defaults to 5)
  def manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]]

  //this is the actual number of faulty nodes in this simulation
  lazy val actualNumberOfFaultyNodes: Int = math.floor(numberOfNodes * maxFractionOfFaultyNodes).toInt

  private lazy val defaultTolerance: Int = {
    val n = averageNumberOfActiveNodes.toInt
    if (n % 2 == 0)
      n / 2 - 1
    else
      n / 2
  }

  //this is the number "f" used in the protocol spec
  //i.e. the maximal number of faulty nodes Hare protocol can tolerate
  def faultyNodesTolerance: Int = defaultTolerance
  def zombieIterationsLimit: Int = 0
  def ignoreSecondNotifyFromTheSameSender: Boolean = false
  def resetNotificationsCounterAtEveryIteration: Boolean = true
}
