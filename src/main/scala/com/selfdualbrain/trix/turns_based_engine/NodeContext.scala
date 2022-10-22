package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, Message, NodeId, Round}

trait NodeContext {
  def iteration: Int
  def currentRound: Round
  def amIActiveInCurrentRound: Boolean
  def broadcast(msg: Message): Unit
  def broadcastIncludingMyself(msg: Message): Unit
  def send(msg: Message, destination: NodeId): Unit
  def inbox(): Iterable[Message]
  def rng: RandomNumberGenerator

  /**
   * Signals achieving the end of the consensus protocol.
   *
   * Caution: malicious" node implementations should not signal "termination of protocol" if they want to participate in communication forever.
   * In "real life" termination of protocol in an internal flag of a node, here we share it with simulation engine to make the simulator
   * API more comfortable
   *
   * @param coll result of consensus (collection of marbles that the terminating node believes to be the "final" answer to the consensus problem
   */
  def signalProtocolTermination(coll: CollectionOfMarbles): Unit

  def consensusResult: Option[CollectionOfMarbles]
}
