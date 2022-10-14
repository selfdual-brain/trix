package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.NodeId

import scala.collection.mutable

abstract class Node(id: NodeId, simConfig: Config, nodeContext: NodeContext) {
  override def toString: String = s"node-$id"

  protected val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]

  def executeSendingPhase(): Unit

  //returns true if protocol termination was achieved
  def executeCalculationPhase(): Boolean
}
