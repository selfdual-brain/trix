package com.selfdualbrain.trix.protocol_model

case class CommitCertificate(acceptedSet: CollectionOfMarbles, iteration: Int, commitMessages: Array[Message.Commit]) {
  override def toString: String = s"CommitCertificate(iteration=$iteration, acceptedSet=$acceptedSet, commit messages=${commitMessages.mkString(",")})"
}
