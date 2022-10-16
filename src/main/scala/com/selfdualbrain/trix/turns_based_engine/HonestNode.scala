package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, Message, NodeId, Round}

import scala.collection.mutable

class HonestNode(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles) extends Node(id, simConfig, context, inputSet) {
  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]
  private var certifiedIteration: Int = -1
  private var currentConsensusApproximation: CollectionOfMarbles = inputSet

  override def executeSendingPhase(): Unit = {
    context.currentRound match {
      case Round.Preround =>
        context.broadcast(Message.Preround(id, inputSet))

      case Round.Status => ???

      case Round.Proposal => ???

      case Round.Commit => ???

      case Round.Notify => ???
    }
  }

  override def executeCalculationPhase(): Boolean = {
    context.currentRound match {
      case Round.Preround => ???


      case Round.Status => ???

      case Round.Proposal => ???

      case Round.Commit => ???

      case Round.Notify => ???

    }

  }

}
