package com.selfdualbrain.trix.protocol_model

sealed abstract class Message {
}

object Message {

  case class Preround(
                       creator: ValidatorId,
                       inputSet: CollectionOfMarbles
                     ) extends Message

  case class Status(
                     creator: ValidatorId,
                     iteration: Int,
                     certifiedIteration: Int,
                     acceptedSet: CollectionOfMarbles
                   ) extends Message

  case class Proposal(
                       creator: ValidatorId,
                       iteration: Int,
                       acceptedSet: CollectionOfMarbles,
                       safeValueProof: SafeValueProof
                     ) extends Message

  case class Commit(
                     creator: ValidatorId,
                     iteration: Int,
                     commitCandidate: CollectionOfMarbles
                   ) extends Message

  case class Notify(
                     creator: ValidatorId,
                     iteration: Int,
                     commitCertificate: CommitCertificate
                   ) extends Message
}
