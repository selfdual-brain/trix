package com.selfdualbrain.trix.protocol_model

sealed abstract class SafeValueProof {
  def safeValue: CollectionOfMarbles
}

object SafeValueProof {

  case class Bootstrap(iteration: Int, statusMessages: Set[Message.Status], magmaSet: CollectionOfMarbles) extends SafeValueProof {
    override def safeValue: CollectionOfMarbles = magmaSet
  }

  case class Proper(iteration: Int, statusMessages: Set[Message.Status], magicMessage: Message.Status) extends SafeValueProof {
    override def safeValue: CollectionOfMarbles = magicMessage.acceptedSet
  }

}
