package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.data_structures.FastIntMap
import com.selfdualbrain.trix.data_structures.{Counter, IndexedBatteryOfIntCounters}
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, CommitCertificate, Marble, Message, NodeId, Round, SafeValueProof}

import scala.collection.mutable

class HonestNode(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles) extends Node(id, simConfig, context, inputSet) {
  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]
  private var certifiedIteration: Int = -1
  private var currentConsensusApproximation: CollectionOfMarbles = inputSet
  private var commitCandidate: Option[CollectionOfMarbles] = None
  private var preroundMessagesSnapshot: Option[Iterable[Message]] = None
  private var marblesWithEnoughSupport: Set[Marble] = Set.empty
  private var latestValidStatusMessages: Set[Message.Status] = Set.empty
  private var lastLocallyFormedCommitCertificate: Option[CommitCertificate] = None
  private val notifyMessagesCounter = new mutable.HashMap[CollectionOfMarbles, mutable.HashSet[NodeId]]

  override def executeSendingPhase(): Unit = {
    context.currentRound match {
      case Round.Preround =>
        context.broadcastIncludingMyself(Message.Preround(id, inputSet))

      case Round.Status =>
        context.broadcastIncludingMyself(
          Message.Status(id, context.iteration, certifiedIteration, acceptedSet = currentConsensusApproximation)
        )

      case Round.Proposal =>
        if (certifiedIteration == -1) {
          val bufferOfMarbles = new mutable.HashSet[Marble]
          for (statusMsg <- latestValidStatusMessages)
            bufferOfMarbles.addAll(statusMsg.acceptedSet.elements)
          val magmaSet = new CollectionOfMarbles(bufferOfMarbles.toSet)
          val svp = SafeValueProof.Bootstrap(context.iteration, latestValidStatusMessages, magmaSet)
          context.broadcastIncludingMyself(Message.Proposal(id, context.iteration, svp, fakeHash = context.rng.nextLong()))
        } else {
          ???
          //todo
        }

      case Round.Commit =>
        if (commitCandidate.isDefined)
          context.broadcastIncludingMyself(Message.Commit(id, context.iteration, commitCandidate.get))

      case Round.Notify =>
        if (lastLocallyFormedCommitCertificate.isDefined)
          context.broadcastIncludingMyself(Message.Notify(id, context.iteration, lastLocallyFormedCommitCertificate.get))
    }
  }

  override def executeCalculationPhase(): Boolean = {
    context.currentRound match {

      case Round.Preround =>
        preroundMessagesSnapshot = Some(context.inbox())
        val counters = new IndexedBatteryOfIntCounters(allowNegativeValues = false)
        for (msg <- filterEquivocations(context.inbox()).asInstanceOf[Iterable[Message.Preround]]) {
          for (marble <- msg.inputSet.elements) {
            counters.increment(marble, 1)
          }
        }
        marblesWithEnoughSupport = counters.indexesWithBalanceAtLeast(simConfig.faultyNodesTolerance + 1).toSet
        currentConsensusApproximation = new CollectionOfMarbles(currentConsensusApproximation.elements.intersect(marblesWithEnoughSupport))
        return false

      case Round.Status =>
        val allStatusMessages: Iterable[Message.Status] = filterEquivocations(context.inbox()).asInstanceOf[Iterable[Message.Status]]
        latestValidStatusMessages = allStatusMessages.filter(msg => msg.acceptedSet.elements.subsetOf(marblesWithEnoughSupport)).toSet
        return false

      case Round.Proposal =>
        val allProposalMessages: Iterable[Message.Proposal] = filterEquivocations(context.inbox()).asInstanceOf[Iterable[Message.Proposal]]

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

          //todo: validation of svp
          commitCandidate = Some(bestMsgSoFar.safeValueProof.safeValue)
        } else {
          commitCandidate = None
        }

        return false

      case Round.Commit =>
        if (commitCandidate.isDefined) {
          val allCommitMessages = filterEquivocations(context.inbox()).asInstanceOf[Iterable[Message.Commit]]
          val commitMessagesVotingOnOurCandidate = allCommitMessages.filter(msg => msg.commitCandidate == commitCandidate.get)
          val howManyOfThem = commitMessagesVotingOnOurCandidate.size
          if (howManyOfThem >= simConfig.faultyNodesTolerance + 1) {
            lastLocallyFormedCommitCertificate = Some(CommitCertificate(acceptedSet = commitCandidate.get, context.iteration, commitMessagesVotingOnOurCandidate.toArray))
            currentConsensusApproximation = commitCandidate.get
          } else {
            lastLocallyFormedCommitCertificate = None
          }
        } else {
          lastLocallyFormedCommitCertificate = None
        }

        return false

      case Round.Notify =>
        val allNotifyMessages = filterEquivocations(context.inbox()).asInstanceOf[Iterable[Message.Notify]]
        val notifyMessagesWithGreaterCertifiedIteration = allNotifyMessages.filter(msg => msg.commitCertificate.iteration >= certifiedIteration)

        //checking if the "wild case" can ever happen
        //the math paper is not clear on what to do with this wild case
        if (notifyMessagesWithGreaterCertifiedIteration.size > 1) {
          val distinctVotes = notifyMessagesWithGreaterCertifiedIteration.map(msg => msg.commitCertificate.acceptedSet).toSet
          if (distinctVotes.size > 1) {
            throw new RuntimeException(s"we need to talk to Julian, distinct votes: $distinctVotes")
          }
        }

        //we update current consensus approximation once we get a notify message with a better certificate than last we knew about
        if (notifyMessagesWithGreaterCertifiedIteration.nonEmpty) {
          val goodNotifyMsg = notifyMessagesWithGreaterCertifiedIteration.head
          currentConsensusApproximation = goodNotifyMsg.commitCertificate.acceptedSet
        }

        //update the counter of notify messages
        var happyToTerminate = false
        for (msg <- allNotifyMessages) {
          val setInQuestion = msg.commitCertificate.acceptedSet
          notifyMessagesCounter.get(setInQuestion) match {
            case None =>
              val coll = new mutable.HashSet[NodeId]
              coll += msg.creator
              if (coll.size >= simConfig.faultyNodesTolerance + 1)
                happyToTerminate = true

            case Some(coll) =>
              coll += msg.creator
              if (coll.size >= simConfig.faultyNodesTolerance + 1)
                happyToTerminate = true
          }
        }

        return happyToTerminate

    }

  }

  /*                              PRIVATE                          */

  /**
   * We filter out messages which are equivocations (i.e. same type of message in the same round from the same sender).
   * Moreover we mark such a sender as malicious (by adding it to the equivocators collection).
   *
   * @param Iterable
   */
  private def filterEquivocations(messages: Iterable[Message]): Iterable[Message] = {
    //todo
//    val sendersIndex = new mutable.HashSet[Int](simConfig.numberOfNodes, 0.75)
//    if (sendersIndex.contains(sender)) {
//      equivocators += sender
//    } else {
//      sendersIndex += sender
//      for (marble <- preroundMsg.inputSet.elements)
//        counters.increment(marble, 1)
//    }

    ???
  }

}
