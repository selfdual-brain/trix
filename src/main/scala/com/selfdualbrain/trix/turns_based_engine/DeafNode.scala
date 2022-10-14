package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.NodeId

class DeafNode(id: NodeId, simConfig: Config, nodeContext: NodeContext) extends Node(id, simConfig, nodeContext) {
  override def executeSendingPhase(): Unit = ???

  override def executeCalculationPhase(): Boolean = ???
}
