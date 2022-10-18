package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{Message, NodeId, Round}
import org.apache.commons.math3.random.MersenneTwister

trait NodeContext {
  def iteration: Int
  def currentRound: Round
  def amIActiveInCurrentRound: Boolean
  def broadcast(msg: Message): Unit
  def broadcastIncludingMyself(msg: Message): Unit
  def send(msg: Message, destination: NodeId): Unit
  def inbox(): Iterable[Message]
  def rng: MersenneTwister

  //"malicious" node implementations should not signal "termination of protocol"
  //if they want to participate in communication forever
  //in "real life" termination of protocol in an internal flag of a node, here we share it with
  //simulation engine to make the simulator API more comfortable
  def signalProtocolTermination(): Unit
}
