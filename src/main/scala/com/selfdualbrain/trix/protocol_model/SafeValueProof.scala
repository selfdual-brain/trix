package com.selfdualbrain.trix.protocol_model

sealed abstract class SafeValueProof{
}

object SafeValueProof {
  case class Bootstrap(iteration: Int, statusMessages: Array[Message.Status], acceptedSet: CollectionOfMarbles) extends SafeValueProof
  case class Proper(iteration: Int, statusMessages: Array[Message.Status], acceptedSet: CollectionOfMarbles) extends SafeValueProof
}
