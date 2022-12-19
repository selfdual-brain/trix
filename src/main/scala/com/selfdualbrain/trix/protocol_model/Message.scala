package com.selfdualbrain.trix.protocol_model

sealed abstract class Message {
  def sender: NodeId
  def iteration: Int
}

object Message {

  case class Preround(
                       sender: NodeId,
                       inputSet: CollectionOfMarbles
                     ) extends Message {

    override def iteration: Int = 0

  }

  case class Status(
                     sender: NodeId,
                     iteration: Int,
                     certifiedIteration: Int,
                     acceptedSet: CollectionOfMarbles
                   ) extends Message

  case class Proposal(
                       sender: NodeId,
                       iteration: Int,
                       safeValueProof: SafeValueProof,
                       fakeHash: Long //used for elimination of too many leaders; in real implementation
                     ) extends Message

  case class Commit(
                     sender: NodeId,
                     iteration: Int,
                     commitCandidate: CollectionOfMarbles
                   ) extends Message

  case class Notify(
                     sender: NodeId,
                     iteration: Int,
                     commitCertificate: CommitCertificate
                   ) extends Message
}
