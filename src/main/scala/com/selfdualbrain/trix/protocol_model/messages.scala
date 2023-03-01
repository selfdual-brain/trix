package com.selfdualbrain.trix.protocol_model

import com.selfdualbrain.trix.cryptography.Hash

/*                                               MESSAGES                                               */

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
                       inputSet: CollectionOfMarbles,
                       eligibilityProof: VrfProof,
                       signature: Ed25519Sig
                     ) extends Message {

    override def iteration: Int = 0

  }

  case class Status(
                     sender: NodeId,
                     iteration: Int,
                     certifiedIteration: Int,
                     acceptedSet: CollectionOfMarbles,
                     eligibilityProof: VrfProof,
                     signature: Ed25519Sig
                   ) extends Message

  case class Proposal(
                       sender: NodeId,
                       iteration: Int,
                       safeValueProof: SafeValueProof,
                       eligibilityProof: VrfProof,
                       signature: Ed25519Sig
                     ) extends Message

  @deprecated
  case class Commit(
                     sender: NodeId,
                     iteration: Int,
                     commitCandidate: CollectionOfMarbles,
                     eligibilityProof: VrfProof,
                     signature: Ed25519Sig
                   ) extends Message

  case class CompactCommit(
                      sender: NodeId,
                      iteration: Int,
                      commitCandidateHash: Hash,
                      eligibilityProof: VrfProof,
                      signature: Ed25519Sig
                   ) extends Message


  @deprecated
  case class Notify(
                     sender: NodeId,
                     iteration: Int,
                     commitCertificate: CommitCertificate,
                     eligibilityProof: VrfProof,
                     signature: Ed25519Sig
                   ) extends Message

  case class CompactNotify(
                      sender: NodeId,
                      iteration: Int,
                      commitCertificate: CompactCommitCertificate,
                      eligibilityProof: VrfProof,
                      signature: Ed25519Sig
                   ) extends Message
}

/*                                               COMMIT CERTIFICATE                                               */

@deprecated
case class CommitCertificate(acceptedSet: CollectionOfMarbles, iteration: Int, commitMessages: Array[Message.Commit]) {
  override def toString: String = s"CommitCertificate(iteration=$iteration, acceptedSet=$acceptedSet, commit messages=${commitMessages.mkString(",")})"
}

case class CompactCommitCertificate(acceptedSet: CollectionOfMarbles, iteration: Int, commitMessages: Array[SqueezedCommitInfo]) {
  override def toString: String = s"CommitCertificate(iteration=$iteration, acceptedSet=$acceptedSet, commit messages=${commitMessages.mkString(",")})"
}

case class SqueezedCommitInfo(sender: NodeId, eligibilityProof: VrfProof, signature: Ed25519Sig)


/*                                               SAFE VALUE PROOF                                               */

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
