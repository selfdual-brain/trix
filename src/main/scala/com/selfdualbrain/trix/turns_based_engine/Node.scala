package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.NodeId

abstract class Node(id: NodeId, simConfig: Config, nodeContext: NodeContext) {
  override def toString: String = s"node-$id"

}
