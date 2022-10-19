package com.selfdualbrain.trix.protocol_model

sealed abstract class Message {
  def creator: NodeId
  def iteration: Int
}

object Message {

  case class Preround(
                       creator: NodeId,
                       inputSet: CollectionOfMarbles
                     ) extends Message {

    override def iteration: Int = 0

  }

  case class Status(
                     creator: NodeId,
                     iteration: Int,
                     certifiedIteration: Int,
                     acceptedSet: CollectionOfMarbles
                   ) extends Message

  case class Proposal(
                       creator: NodeId,
                       iteration: Int,
                       safeValueProof: SafeValueProof,
                       fakeHash: Long //used for elimination of too many leaders; in real implementation
                     ) extends Message

  case class Commit(
                     creator: NodeId,
                     iteration: Int,
                     commitCandidate: CollectionOfMarbles
                   ) extends Message

  case class Notify(
                     creator: NodeId,
                     iteration: Int,
                     commitCertificate: CommitCertificate
                   ) extends Message
}
