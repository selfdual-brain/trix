package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.NodeId

trait SimEngine {
  def playNextRound(): Unit
  def nodesWhichTerminated(): Set[NodeId]
}
