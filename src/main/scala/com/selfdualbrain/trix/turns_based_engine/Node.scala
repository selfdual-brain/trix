package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

import scala.collection.mutable

abstract class Node(val id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles) {
  override def toString: String = s"node-$id"

  def executeSendingPhase(): Unit

  //returns true if protocol termination was achieved
  def executeCalculationPhase(): Boolean
}
