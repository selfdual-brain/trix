package com.selfdualbrain.trix.turns_based_engine.nodes

import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.cryptography.Hash
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model._
import com.selfdualbrain.trix.turns_based_engine.{Config, Node, NodeContext, NodeStats}

import scala.collection.mutable

/**
 * We follow the math paper on Hare, with one exception: we persistently blacklist all discovered equivocators.
 */
class HonestNodeFollowingThePaper(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles, out: Option[AbstractTextOutput])
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

  override def stats: NodeStats = NodeStats.EmptyMock

  override def onIterationBegin(iteration: Int): Unit = {
    //do nothing
  }

  override def executeSendingPhase(): Unit = {

    context.currentRound match {
      case Round.Preround =>
        context.broadcastIncludingMyself(Message.Preround(id, inputSet, context.rng.nextLong(), Hash.random(context.rng)))

      case Round.Status =>
        context.broadcastIncludingMyself(
          Message.Status(id, context.iteration, certifiedIteration, acceptedSet = currentConsensusApproximation, context.rng.nextLong(), Hash.random(context.rng))
        )

      case Round.Proposal =>
        output("leader", s"latest valid status messages: $latestValidStatusMessages")
        val svp: Option[SafeValueProof] =
          if (certifiedIteration == -1) { //todo: this condition is simply wrong and must be replaced (does not reflect the definition of safe value proof)
            val bufferOfMarbles = new mutable.HashSet[Marble]
            val statusMessagesToBeConsidered = latestValidStatusMessages.filter(msg => msg.acceptedSet.elements.subsetOf(marblesWithEnoughSupport)).toSet
            if (statusMessagesToBeConsidered.size < simConfig.faultyNodesTolerance + 1)
              None
          else {
            for (statusMsg <- statusMessagesToBeConsidered)
              bufferOfMarbles.addAll(statusMsg.acceptedSet.elements)
            val magmaSet = new CollectionOfMarbles(bufferOfMarbles.toSet)
            output("leader-magma-set", s"$magmaSet")
            Some(SafeValueProof.Bootstrap(context.iteration, statusMessagesToBeConsidered, magmaSet))
          }
        } else {
          if (latestValidStatusMessages.isEmpty) {
            output("leader-svp-fail", "no valid status messages")
            None
          } else {
            if (latestValidStatusMessages.size < simConfig.faultyNodesTolerance + 1) {
              output("leader-svp-fail", s"number of status messages less than f+1: ${latestValidStatusMessages.size}")
              None
            } else {
              val maxCertifiedIteration: Int = latestValidStatusMessages.map(msg => msg.certifiedIteration).max
              //todo: check this case in go-spacemesh
              //   if (maxCertifiedIteration < 0)
              //      throw new RuntimeException("Could not form SVP: maxCertifiedIteration < 0")
              if (maxCertifiedIteration >= 0) {
                val messagesWithMaxCertifiedIteration = latestValidStatusMessages.filter(msg => msg.certifiedIteration == maxCertifiedIteration)
                val candidateSets = messagesWithMaxCertifiedIteration.map(msg => msg.acceptedSet)
                if (candidateSets.size > 1) {
                  output("svp-candidate-sets", s"$candidateSets")
                  output("messages-with-max-ci", s"$messagesWithMaxCertifiedIteration")
                  throw new RuntimeException(s"Could not form SVP: max-certified-iteration=$maxCertifiedIteration candidateSets.size = ${candidateSets.size}")
                }
                Some(SafeValueProof.Proper(context.iteration, latestValidStatusMessages, messagesWithMaxCertifiedIteration.head))
              } else {
                output("leader-svp-fail", "max certified iteration (among seen status messages) was -1")
                None
              }
            }
          }
        }

        if (svp.isDefined) {
          output("svp-formed", svp.get.safeValue.toString)
          context.broadcastIncludingMyself(Message.Proposal(id, context.iteration, svp.get, eligibilityProof = context.rng.nextLong(), Hash.random(context.rng)))
        }

      case Round.Commit =>
        commitCandidate match {
          case Some(coll) => context.broadcastIncludingMyself(Message.Commit(id, context.iteration, commitCandidate.get, context.rng.nextLong(), Hash.random(context.rng)))
          case None => output("commit-candidate-not-available", "proposal was missing")
        }

      case Round.Notify =>
        if (lastLocallyFormedCommitCertificate.isDefined)
          context.broadcastIncludingMyself(Message.Notify(id, context.iteration, lastLocallyFormedCommitCertificate.get, context.rng.nextLong(), Hash.random(context.rng)))
    }
  }

  override def executeCalculationPhase(): Option[CollectionOfMarbles] = {
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
        output("marbles-with-enough-support", marblesWithEnoughSupport.toSeq.sorted.mkString(","))
        currentConsensusApproximation = new CollectionOfMarbles(currentConsensusApproximation.elements.intersect(marblesWithEnoughSupport))
        output("consensus-approx-update", currentConsensusApproximation.mkString(","))
        return None

      case Round.Status =>
        val allStatusMessages: Iterable[Message.Status] = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Status]]
        latestValidStatusMessages = allStatusMessages.toSet
//        latestValidStatusMessages = allStatusMessages.filter(msg => msg.acceptedSet.elements.subsetOf(marblesWithEnoughSupport)).toSet
        return None

      case Round.Proposal =>
        val allProposalMessages: Iterable[Message.Proposal] = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Proposal]]

        if (allProposalMessages.nonEmpty) {
          //enforce there is at most one leader (finding the proposal message with smallest fake hash)
          var bestMsgSoFar: Message.Proposal = allProposalMessages.head
          var bestHashSoFar: Long = bestMsgSoFar.eligibilityProof
          for (msg <- allProposalMessages) {
            if (msg.eligibilityProof < bestMsgSoFar.eligibilityProof) {
              bestMsgSoFar = msg
              bestHashSoFar = msg.eligibilityProof
            }
          }

          //todo: validation of svp
          output("accepting-winning-proposal", bestMsgSoFar.toString)
          commitCandidate = Some(bestMsgSoFar.safeValueProof.safeValue)
        } else {
          output("proposal-is-missing", "")
          commitCandidate = None
        }

        return None

      case Round.Commit =>
        if (commitCandidate.isDefined) {
          val allCommitMessages = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Commit]]
          val commitMessagesVotingOnOurCandidate = allCommitMessages.filter(msg => msg.commitCandidate == commitCandidate.get)
          val howManyOfThem = commitMessagesVotingOnOurCandidate.size
          if (howManyOfThem >= simConfig.faultyNodesTolerance + 1) {
            lastLocallyFormedCommitCertificate = Some(CommitCertificate(acceptedSet = commitCandidate.get, context.iteration, commitMessagesVotingOnOurCandidate.toArray))
            currentConsensusApproximation = commitCandidate.get
            output("commit-certificate", lastLocallyFormedCommitCertificate.toString)
          } else {
            output ("commit-certificate", "[none]")
            lastLocallyFormedCommitCertificate = None
          }
        } else {
          lastLocallyFormedCommitCertificate = None
        }

        return None

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
        var consensusResult: Option[CollectionOfMarbles] = None

        for (msg <- allNotifyMessages) {
          val setInQuestion: CollectionOfMarbles = msg.commitCertificate.acceptedSet
          notifyMessagesCounter.get(setInQuestion) match {
            case None =>
              val coll = new mutable.HashSet[NodeId]
              coll += msg.sender
              notifyMessagesCounter += setInQuestion -> coll

            case Some(coll) =>
              coll += msg.sender
              if (coll.size >= simConfig.faultyNodesTolerance + 1) {
                if (consensusResult.isEmpty)
                  consensusResult = Some(setInQuestion)
              }
          }
        }
        output("notify-counters", notifyMessagesCounterPrettyPrint())
        if (consensusResult.nonEmpty)
          output("terminating", s"consensus=${consensusResult.get}")
        return consensusResult
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
    for (msg <- honestMessages)
      output("incoming-message", msg.toString)
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
