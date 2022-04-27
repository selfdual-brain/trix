package com.selfdualbrain.trix.protocol_model

sealed abstract class Round(val number: Int) {
}

object Round {
  case object Preround extends Round(0)
  case object Status extends Round(1)
  case object Proposal extends Round(2)
  case object Commit extends Round(3)
  case object Notify extends Round(4)
}
