package com.selfdualbrain.trix.protocol_model

sealed abstract class Message {
}

object Message {

  case class Preround(
                       creator: NodeId,
                       inputSet: CollectionOfMarbles
                     ) extends Message

  case class Status(
                     creator: NodeId,
                     iteration: Int,
                     certifiedIteration: Int,
                     acceptedSet: CollectionOfMarbles
                   ) extends Message

  case class Proposal(
                       creator: NodeId,
                       iteration: Int,
                       acceptedSet: CollectionOfMarbles,
                       safeValueProof: SafeValueProof
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
