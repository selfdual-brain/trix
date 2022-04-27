package com.selfdualbrain.trix.engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, Message, Round, ValidatorId}

sealed abstract class TrixEventPayload {

}

object TrixEventPayload {

  //#################### TRANSPORT ####################

  case class GossipDelivery(msg: Message) extends TrixEventPayload

  case class DirectCommDelivery(msg: Message) extends TrixEventPayload

  //#################### LOOPBACK ####################

  case class BeginOfNewRound(iteration: Int, round: Round) extends TrixEventPayload

  //#################### SEMANTIC ####################

  case class EquivocationDetected(validator: ValidatorId) extends TrixEventPayload

  case class ConsensusAchieved(iteration: Int, round: Round, agreedCollection: CollectionOfMarbles) extends TrixEventPayload

}