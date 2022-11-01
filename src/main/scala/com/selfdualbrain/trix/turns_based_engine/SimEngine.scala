package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{NodeId, Round}

trait SimEngine {
  def playNextRound(): Unit
  def nodesWhichTerminated: Iterator[NodeId]
  def numberOfNodesWhichTerminated: Int
  def numberOfRoundsWithTermination: Int
  def reachedTerminationOfProtocol(nodeId: NodeId): Boolean
  def currentRound: Option[Round]
  def currentIteration: Int
  def measuredLostMessagesFraction: Double
  def nodeStats(nodeId: Int): NodeStats
}
