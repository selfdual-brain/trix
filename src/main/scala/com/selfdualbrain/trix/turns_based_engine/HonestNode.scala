package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.data_structures.FastIntMap
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, Message, NodeId, Round}

import scala.collection.mutable

class HonestNode(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles) extends Node(id, simConfig, context, inputSet) {
  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]
  private var certifiedIteration: Int = -1
  private var currentConsensusApproximation: CollectionOfMarbles = inputSet
  private var preroundMessagesSnapshot: Option[Iterable[Message]] = None

  override def executeSendingPhase(): Unit = {
    context.currentRound match {
      case Round.Preround =>
        context.broadcast(Message.Preround(id, inputSet))

      case Round.Status =>
        if (context.amIActiveInCurrentRound) {
          context.broadcast(Message.Status(id, context.iteration, certifiedIteration, acceptedSet = currentConsensusApproximation))
        }

      case Round.Proposal => ???

      case Round.Commit => ???

      case Round.Notify => ???
    }
  }

  override def executeCalculationPhase(): Boolean = {
    context.currentRound match {

      case Round.Preround =>
        preroundMessagesSnapshot = Some(context.inbox())
        val sendersIndex = new mutable.HashSet[Int](simConfig.numberOfNodes, 0.75)
        val counters = new IndexedBatteryOfIntCounters(allowNegativeValues = false)
        for (msg <- context.inbox()) {
          val preroundMsg = msg.asInstanceOf[Message.Preround]
          val sender = preroundMsg.creator
          if (sendersIndex.contains(sender)) {
            equivocators += sender
          } else {
            sendersIndex += sender
            for (marble <- preroundMsg.inputSet.elements)
              counters.increment(marble, 1)
          }
        }
        val marblesWithEnoughSupport = counters.indexesWithBalanceAtLeast(simConfig.faultyNodesTolerance + 1).toSet
        currentConsensusApproximation = new CollectionOfMarbles(currentConsensusApproximation.elements.intersect(marblesWithEnoughSupport))

      case Round.Status =>


      case Round.Proposal => ???

      case Round.Commit => ???

      case Round.Notify => ???

    }

    return false
  }

}
