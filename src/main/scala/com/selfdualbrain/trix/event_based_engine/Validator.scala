package com.selfdualbrain.trix.event_based_engine

import com.selfdualbrain.trix.protocol_model.{Message, Round, NodeId}

/**
 * Defines features of an agent ("validator") to be compatible with TrixEngine.
 * The engine uses this trait for delivering events to agents.
 */
trait Validator {

/**
 * Validator id.
 *
 * This is the identifier of consensus-protocol-level agent/process.
 * In the protocol, validatorId is used in the 'creator' field of every message.
 * If malicious players join the P2P network they may create many nodes sharing the same validator id.
 * In a perfectly healthy network with honest players only, validator-id maps 1-1 to P2P node id.
 */
  def validatorId: NodeId

  /**
   * Node id (peer-to-peer network member address).
   *
   * This is the identifier of the communication-level agent/process. On the level of simulation engine,
   * this id is used as the DES agent-id.
   */
  def nodeId: P2PNode

  /**
   * Called by the engine at the beginning of this agent existence.
   * Gives this agent the chance to self-initialize.
   */
  def startup(): Unit

  /**
   * A message has been delivered to this agent.
   * This delivery happens because of other agent calling broadcast().
   *
   * (this is handler's entry point)
   */
  def onNewMessageArrived(msg: Message): Unit

  /**
   * According to the clock, a new round of the protocol has just started.
   *
   * (this is handler's entry point)
   */
  def onRoundStarted(iteration: Int, round: Round): Unit

}
