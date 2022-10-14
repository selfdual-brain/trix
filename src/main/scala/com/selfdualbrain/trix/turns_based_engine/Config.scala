package com.selfdualbrain.trix.turns_based_engine

trait Config {
  def eligibilityRngMasterSeed: Long
  def msgDeliveryRngSeed: Long
  def maxFractionOfMaliciousNodes: Double
  def fractionOfDeafNodesAmongMalicious: Double
  def inputSetSizeRange: (Int, Int)
  def initialSizeOfInboxBuffer: Int
  def marblesRangeForHonestNodes: Int
  def isNetworkReliable: Boolean
  def probabilityOfAMessageGettingLost: Double
  def numberOfNodes: Int
  def averageNumberOfActiveNodes: Double
  def averageNumberOfLeaders: Double
  def maxNumberOfIterations: Int
}
