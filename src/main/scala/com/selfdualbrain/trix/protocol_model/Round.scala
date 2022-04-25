package com.selfdualbrain.trix.protocol_model

sealed abstract class Round {
}

object Round {
  case object Preround extends Round
  case object Status extends Round
  case object Proposal extends Round
  case object Commit extends Round
  case object Notify extends Round
}
