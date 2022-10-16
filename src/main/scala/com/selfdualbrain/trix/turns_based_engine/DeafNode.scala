package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

class DeafNode(id: NodeId, simConfig: Config, nodeContext: NodeContext, inputSet: CollectionOfMarbles) extends Node(id, simConfig, nodeContext, inputSet) {
  override def executeSendingPhase(): Unit = ???

  override def executeCalculationPhase(): Boolean = ???
}
