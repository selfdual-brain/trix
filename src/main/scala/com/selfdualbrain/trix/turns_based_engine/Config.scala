package com.selfdualbrain.trix.turns_based_engine

trait Config {
  def maxFractionOfMaliciousNodes: Double
  def fractionOfDeafNodesAmongMalicious: Double
  def isNetworkReliable: Boolean
  def probabilityOfAMessageGettingLost: Double
  def numberOfNodes: Int
  def averageNumberOfActiveNodes: Double
  def averageNumberOfLeaders: Double
  def maxNumberOfIterations: Int
}
