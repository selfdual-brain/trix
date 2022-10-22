package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model._

import scala.collection.mutable

class HonestNode(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles, out: AbstractTextOutput)
  extends Node(id, simConfig, context, inputSet, out) {

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
        val svp: Option[SafeValueProof] = if (certifiedIteration == -1) {
          val bufferOfMarbles = new mutable.HashSet[Marble]
          for (statusMsg <- latestValidStatusMessages)
            bufferOfMarbles.addAll(statusMsg.acceptedSet.elements)
          val magmaSet = new CollectionOfMarbles(bufferOfMarbles.toSet)
          Some(SafeValueProof.Bootstrap(context.iteration, latestValidStatusMessages, magmaSet))
        } else {
          if (latestValidStatusMessages.isEmpty)
            None
          else {
            val maxCertifiedIteration: Int = latestValidStatusMessages.map(msg => msg.certifiedIteration).max

//            todo: check this case in go-spacemesh
//            if (maxCertifiedIteration < 0)
//              throw new RuntimeException("Could not form SVP: maxCertifiedIteration < 0")

            if (maxCertifiedIteration >= 0) {
              val messagesWithMaxCertifiedIteration = latestValidStatusMessages.filter(msg => msg.certifiedIteration == maxCertifiedIteration)
              val candidateSets = messagesWithMaxCertifiedIteration.map(msg => msg.acceptedSet)
              if (candidateSets.size > 1)
                throw new RuntimeException(s"Could not form SVP: candidateSets.size = ${candidateSets.size}")
              Some(SafeValueProof.Proper(context.iteration, latestValidStatusMessages, messagesWithMaxCertifiedIteration.head))
            } else
              None
          }
        }

        if (svp.isDefined) {
          output("proposal-by-honest-leader", svp.get.safeValue.toString)
          context.broadcastIncludingMyself(Message.Proposal(id, context.iteration, svp.get, fakeHash = context.rng.nextLong()))
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
        for (msg <- filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Preround]]) {
          for (marble <- msg.inputSet.elements) {
            counters.increment(marble, 1)
          }
        }
        marblesWithEnoughSupport = counters.indexesWithBalanceAtLeast(simConfig.faultyNodesTolerance + 1).toSet
        output("marbles-with-enough-support", marblesWithEnoughSupport.mkString(","))
        currentConsensusApproximation = new CollectionOfMarbles(currentConsensusApproximation.elements.intersect(marblesWithEnoughSupport))
        output("consensus-approx-update", currentConsensusApproximation.mkString(","))
        return false

      case Round.Status =>
        val allStatusMessages: Iterable[Message.Status] = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Status]]
        latestValidStatusMessages = allStatusMessages.filter(msg => msg.acceptedSet.elements.subsetOf(marblesWithEnoughSupport)).toSet
        return false

      case Round.Proposal =>
        val allProposalMessages: Iterable[Message.Proposal] = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Proposal]]

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
          output("accepting-winning-proposal", bestMsgSoFar.toString)
          commitCandidate = Some(bestMsgSoFar.safeValueProof.safeValue)
        } else {
          output("proposal-is-missing", "")
          commitCandidate = None
        }

        return false

      case Round.Commit =>
        if (commitCandidate.isDefined) {
          val allCommitMessages = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Commit]]
          val commitMessagesVotingOnOurCandidate = allCommitMessages.filter(msg => msg.commitCandidate == commitCandidate.get)
          val howManyOfThem = commitMessagesVotingOnOurCandidate.size
          if (howManyOfThem >= simConfig.faultyNodesTolerance + 1) {
            lastLocallyFormedCommitCertificate = Some(CommitCertificate(acceptedSet = commitCandidate.get, context.iteration, commitMessagesVotingOnOurCandidate.toArray))
            currentConsensusApproximation = commitCandidate.get
            output("consensus-approx-update", currentConsensusApproximation.mkString(","))
          } else {
            lastLocallyFormedCommitCertificate = None
          }
        } else {
          lastLocallyFormedCommitCertificate = None
        }

        return false

      case Round.Notify =>
        val allNotifyMessages = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Notify]]
        val notifyMessagesWithGreaterCertifiedIteration = allNotifyMessages.filter(msg => msg.commitCertificate.iteration >= certifiedIteration)

        //checking if the "wild case" of distinct votes can ever happen
        //the math paper is not clear on what to do with this wild case
        //if such situations really happen, we need to invent proper handling of them
        if (notifyMessagesWithGreaterCertifiedIteration.size > 1) {
          val distinctVotes = notifyMessagesWithGreaterCertifiedIteration.map(msg => msg.commitCertificate.acceptedSet).toSet
          if (distinctVotes.size > 1) {
            throw new RuntimeException(s"it looks like 'distinct votes problem' really can happen: $distinctVotes")
          }
        }

        //we update current consensus approximation once we get a notify message with a better certificate than last we knew about
        if (notifyMessagesWithGreaterCertifiedIteration.nonEmpty) {
          val goodNotifyMsg = notifyMessagesWithGreaterCertifiedIteration.head
          currentConsensusApproximation = goodNotifyMsg.commitCertificate.acceptedSet
          output("consensus-approx-update", currentConsensusApproximation.mkString(","))
          certifiedIteration = goodNotifyMsg.commitCertificate.iteration
        }

        //update the counter of notify messages
        var happyToTerminate = false
        for (msg <- allNotifyMessages) {
          val setInQuestion = msg.commitCertificate.acceptedSet
          notifyMessagesCounter.get(setInQuestion) match {
            case None =>
              val coll = new mutable.HashSet[NodeId]
              coll += msg.sender
              notifyMessagesCounter += setInQuestion -> coll

            case Some(coll) =>
              coll += msg.sender
              if (coll.size >= simConfig.faultyNodesTolerance + 1) {
                happyToTerminate = true
                output("terminating", s"consensus=$setInQuestion")
              }
          }
        }

        output("notify-counters", notifyMessagesCounterPrettyPrint())
        return happyToTerminate
    }

  }

  /*                              PRIVATE                          */

  /**
   * We filter out messages which are equivocations (i.e. same type of message in the same round from the same sender
   * but different contents of the message).
   * Moreover we mark such a sender as malicious (by adding it to the equivocators collection).
   * If a node gets marked as an equivocator, all subsequent messages from this node are filtered out (i.e. ignored).
   * We also remove duplicated messages.
   *
   * @param Iterable
   */
  private def filterOutEquivocationsAndDuplicates(messages: Iterable[Message]): Iterable[Message] = {
    val sendersSeenSoFar = new mutable.HashSet[Int](simConfig.numberOfNodes, 0.75)
    val messagesWithDuplicatesRemoved: Set[Message] = messages.toSet
    for (msg <- messagesWithDuplicatesRemoved) {
      if (sendersSeenSoFar.contains(msg.sender))
        equivocators += msg.sender
    }
    val honestMessages: Set[Message] = messagesWithDuplicatesRemoved.filter(msg => !equivocators.contains(msg.sender))
    return honestMessages
  }

  private def notifyMessagesCounterPrettyPrint(): String = {
    if (notifyMessagesCounter.isEmpty)
      return "(empty)"
    else {
      val buf = new mutable.StringBuilder(3000)
      for ((k,v) <- notifyMessagesCounter) {
        buf.append(k)
        buf.append(" -> ")
        buf.append(v)
        buf.append(",")
      }
      return buf.toString()
    }
  }

}
