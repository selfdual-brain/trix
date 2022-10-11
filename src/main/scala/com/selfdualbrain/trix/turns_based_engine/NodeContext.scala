package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.Round

trait NodeContext {
  def iteration: Int
  def currentRound: Option[Round]
}
