package com.selfdualbrain.trix.protocol_model

case class CommitCertificate(acceptedSet: CollectionOfMarbles, iteration: Int, commitMessages: Array[Message.Commit]) {

}
