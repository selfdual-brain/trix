package com.selfdualbrain.trix.protocol_model

import com.selfdualbrain.trix.engine.{P2PNode, Validator}

class HonestFullySynchronousValidator(val id: ValidatorId) extends Validator {
  private var iteration: Int = 0
  private var certifiedIteration: Int = -1
  private var commitCandidate: Option[CollectionOfMarbles] = None

  override def validatorId: ValidatorId = ???

  override def nodeId: P2PNode = ???

  override def startup(): Unit = ???

  override def onNewMessageArrived(msg: Message): Unit = ???

  override def onRoundStarted(iteration: Marble, round: Round): Unit = ???
}
