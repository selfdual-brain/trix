package com.selfdualbrain.trix.protocol_model

trait RoleDistributionOracle {
  def isActive(validator: NodeId, iteration: Int, round: Round): Boolean
}
