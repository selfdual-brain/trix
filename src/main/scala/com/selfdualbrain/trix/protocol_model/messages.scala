package com.selfdualbrain.trix.protocol_model

import com.selfdualbrain.trix.cryptography.Hash

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

  case class CompactCommit(
                      sender: NodeId,
                      iteration: Int,
                      commitCandidateHash: Hash
                   )

  case class Notify(
                     sender: NodeId,
                     iteration: Int,
                     commitCertificate: CommitCertificate
                   ) extends Message

  case class CompactNotify(
                      sender: NodeId,
                      iteration: Int,
                      commitCertificate: CompactCommitCertificate
                   )
}

case class CommitCertificate(
                              acceptedSet: CollectionOfMarbles,
                              iteration: Int,
                              commitMessages: Array[Message.Commit]) {

  override def toString: String = s"CommitCertificate(iteration=$iteration, acceptedSet=$acceptedSet, commit messages=${commitMessages.mkString(",")})"
}

case class CompactCommitCertificate(
                              acceptedSet: CollectionOfMarbles,
                              iteration: Int,
                              commitMessages: Array[Message.CompactCommit]) {
  override def toString: String = s"CommitCertificate(iteration=$iteration, acceptedSet=$acceptedSet, commit messages=${commitMessages.mkString(",")})"
}


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
