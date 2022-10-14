package com.selfdualbrain.trix.protocol_model

sealed abstract class Round(val number: Int, val isLeaderRound: Boolean) {
}

object Round {
  case object Preround extends Round(number = -1, isLeaderRound = false)
  case object Status extends Round(number = 0, isLeaderRound = false)
  case object Proposal extends Round(number = 1, isLeaderRound = true)
  case object Commit extends Round(number = 2, isLeaderRound = false)
  case object Notify extends Round(number = 3, isLeaderRound = false)
}
