package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.data_structures.FastIntMap
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, Marble, Message, NodeId, Round, SafeValueProof}

import scala.collection.mutable

class HonestNode(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles) extends Node(id, simConfig, context, inputSet) {
  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]
  private var certifiedIteration: Int = -1
  private var currentConsensusApproximation: CollectionOfMarbles = inputSet
  private var preroundMessagesSnapshot: Option[Iterable[Message]] = None
  private var marblesWithEnoughSupport: Set[Marble] = Set.empty
  private var latestValidStatusMessages: Set[Message.Status] = Set.empty

  override def executeSendingPhase(): Unit = {
    context.currentRound match {
      case Round.Preround =>
        context.broadcastIncludingMyself(Message.Preround(id, inputSet))

      case Round.Status =>
        context.broadcastIncludingMyself(Message.Status(id, context.iteration, certifiedIteration, acceptedSet = currentConsensusApproximation))

      case Round.Proposal =>
        if (certifiedIteration == -1) {
          val bufferOfMarbles = new mutable.HashSet[Marble]
          for (statusMsg <- latestValidStatusMessages)
            bufferOfMarbles.addAll(statusMsg.acceptedSet.elements)
          val magmaSet = new CollectionOfMarbles(bufferOfMarbles.toSet)
          val svp = SafeValueProof.Bootstrap(context.iteration, latestValidStatusMessages, magmaSet, currentConsensusApproximation)
          context.broadcastIncludingMyself(Message.Proposal(id, context.iteration, currentConsensusApproximation, svp, fakeHash = context.rng.nextLong()))
        } else {
          ???
          //todo
        }

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
          if (sendersIndex.contains(sender)) { //todo: possibly we should just ignore messages from equivocators whatsoever ?
            equivocators += sender
          } else {
            sendersIndex += sender
            for (marble <- preroundMsg.inputSet.elements)
              counters.increment(marble, 1)
          }
        }
        marblesWithEnoughSupport = counters.indexesWithBalanceAtLeast(simConfig.faultyNodesTolerance + 1).toSet
        currentConsensusApproximation = new CollectionOfMarbles(currentConsensusApproximation.elements.intersect(marblesWithEnoughSupport))
        return false

      case Round.Status =>
        val allStatusMessages = context.inbox()
        latestValidStatusMessages = allStatusMessages.filter(msg => msg.asInstanceOf[Message.Status].acceptedSet.elements.subsetOf(marblesWithEnoughSupport))
        return false

      case Round.Proposal =>
        val allProposalMessages: Iterable[Message.Proposal] = context.inbox().asInstanceOf[Iterable[Message.Proposal]]

        if (allProposalMessages.nonEmpty) {
          //enforce there is at most one leader (finding the proposal message with smallest fake hash)
          var bestMsgSoFar: Message.Proposal = allProposalMessages.head
          var bestHashSoFar: Long = bestMsgSoFar.fakeHash
          for (msg <- allProposalMessages) {
            if (msg.fakeHash < bestMsgSoFar.fakeHash) {
              bestMsgSoFar = msg
              bestHashSoFar = msg.fakeHash
            }
          }






        }

        return false

      case Round.Commit => ???

      case Round.Notify => ???

    }

  }

}
