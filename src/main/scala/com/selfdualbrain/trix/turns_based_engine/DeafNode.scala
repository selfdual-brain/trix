package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

class DeafNode(id: NodeId, simConfig: Config, nodeContext: NodeContext, inputSet: CollectionOfMarbles, out: AbstractTextOutput)
  extends Node(id, simConfig, nodeContext, inputSet, out) {

  override def executeSendingPhase(): Unit = {
    //do nothing
  }

  override def executeCalculationPhase(): Boolean = {
    //do nothing
    return false
  }

}
