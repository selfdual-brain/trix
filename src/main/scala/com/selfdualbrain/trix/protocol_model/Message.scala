package com.selfdualbrain.trix.protocol_model

sealed abstract class Message {
  def sender: NodeId
  def iteration: Int
  def isSignatureOK: Boolean = {
    //messages with invalid signatures are currently not simulated
    return true
  }
  def isEligibilityProofOK: Boolean = true
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
                       fakeEligibilityProof: Long //needed to ensure there is at most 1 leader (every node picks the leader with the smallest eligibility proof)
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
