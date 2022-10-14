package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{Message, NodeId, Round}

trait NodeContext {
  def iteration: Int
  def currentRound: Option[Round]
  def isActiveInCurrentRound: Boolean
  def broadcast(msg: Message): Unit
  def send(msg: Message, destination: NodeId): Unit
  def inbox(): Iterable[Message]
}
