package com.selfdualbrain.trix.protocol_model

trait RoleDistributionOracle {
  def isActive(validator: ValidatorId, iteration: Int, round: Round): Boolean
}
