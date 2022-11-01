package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.Marble

sealed abstract class NotifyRoundOverrideCase {}

object NotifyRoundOverrideCase {
  case object NoChange extends NotifyRoundOverrideCase
  case object GoingUp extends NotifyRoundOverrideCase
  case object GoingDown extends NotifyRoundOverrideCase
  case object NonMonotonic extends NotifyRoundOverrideCase

  def recognize(oldSet: Set[Marble], newSet: Set[Marble]): NotifyRoundOverrideCase =
    if (oldSet == newSet)
      NoChange
    else {
      if (oldSet.subsetOf(newSet))
        GoingUp
      else if (newSet.subsetOf(oldSet))
        GoingDown
      else
        NonMonotonic
    }
}
