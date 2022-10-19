package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

class GangNode(id: NodeId, simConfig: Config, nodeContext: NodeContext, inputSet: CollectionOfMarbles, out: AbstractTextOutput)
  extends Node(id, simConfig, nodeContext, inputSet, out) {

  override def executeSendingPhase(): Unit = ???

  override def executeCalculationPhase(): Boolean = ???
}
