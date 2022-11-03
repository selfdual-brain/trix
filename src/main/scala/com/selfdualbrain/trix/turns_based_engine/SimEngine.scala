package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId, Round}

trait SimEngine {
  def playNextRound(): Unit
  def nodesWhichTerminated: Iterator[NodeId]
  def numberOfNodesWhichTerminated: Int
  def numberOfRoundsWithTermination: Int
  def reachedTerminationOfProtocol(nodeId: NodeId): Boolean
  def consensusResult(nodeId: NodeId): Option[CollectionOfMarbles]
  def currentRound: Option[Round]
  def currentIteration: Int
  def measuredLostMessagesFraction: Double
  def nodeStats(nodeId: Int): NodeStats
}
