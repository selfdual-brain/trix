package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.data_structures.FastIntMap
import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model._

import scala.collection.mutable

/**
 * We follow the math paper on Hare, with 2 exceptions:
 * - we persistently blacklist all discovered equivocators
 * - we build safe value proofs following the processing logic as implemented in go-spacemesh (which is different that the one described in the paper)
 */
class HonestNodeFollowingGoSpacemesh(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles, out: Option[AbstractTextOutput])
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
  //iteration ----> map[collectionOfMarbles ---> certificate]
  private val certificates = new FastIntMap[mutable.HashMap[CollectionOfMarbles, CommitCertificate]](100)
  private val localStatistics = new NodeStats {
    var notifyCertificateOverridesWithSetGoingUp: Int = 0
    var notifyCertificateOverridesWithSetGoingDown: Int = 0
    var notifyCertificateOverridesWithNonMonotonicChange: Int = 0
    override def equivocatorsDiscovered: Int = equivocators.size
    var emptyProposalRounds: Int = 0
  }

  override def stats: NodeStats = localStatistics

  override def executeSendingPhase(): Unit = {

    context.currentRound match {
      case Round.Preround =>
        context.broadcastIncludingMyself(Message.Preround(id, inputSet))

      case Round.Status =>
        context.broadcastIncludingMyself(
          Message.Status(id, context.iteration, certifiedIteration, acceptedSet = currentConsensusApproximation)
        )

      case Round.Proposal =>
        output("leader", s"latest valid status messages: $latestValidStatusMessages")
        val justifiedStatusMessages = latestValidStatusMessages.filter(msg => isStatusMessageJustified(msg))

        val svp: Option[SafeValueProof] =
          if (justifiedStatusMessages.size < simConfig.faultyNodesTolerance + 1) {
            output("leader-svp-fail", s"number of valid status messages less than f+1: ${justifiedStatusMessages.size}")
            None
          } else {
            val maxCertifiedIteration: Int = justifiedStatusMessages.map(msg => msg.certifiedIteration).max
            if (maxCertifiedIteration == -1) {
              val bufferOfMarbles = new mutable.HashSet[Marble]
              for (statusMsg <- justifiedStatusMessages)
                bufferOfMarbles.addAll(statusMsg.acceptedSet.elements)
              val magmaSet = new CollectionOfMarbles(bufferOfMarbles.toSet)
              output("leader-magma-set", s"$magmaSet")
              Some(SafeValueProof.Bootstrap(context.iteration, justifiedStatusMessages, magmaSet))
            } else {
              val messagesWithMaxCertifiedIteration = justifiedStatusMessages.filter(msg => msg.certifiedIteration == maxCertifiedIteration)
              val candidateSets = messagesWithMaxCertifiedIteration.map(msg => msg.acceptedSet)
              if (candidateSets.size > 1) {
                output("svp-candidate-sets", s"$candidateSets")
                output("messages-with-max-ci", s"$messagesWithMaxCertifiedIteration")
                throw new RuntimeException(s"Could not form SVP: max-certified-iteration=$maxCertifiedIteration candidateSets.size = ${candidateSets.size}")
              }
              Some(SafeValueProof.Proper(context.iteration, justifiedStatusMessages, messagesWithMaxCertifiedIteration.head))
            }
          }

        if (svp.isDefined) {
          output("svp-formed", svp.get.safeValue.toString)
          context.broadcastIncludingMyself(Message.Proposal(id, context.iteration, svp.get, fakeHash = context.rng.nextLong()))
        }

      case Round.Commit =>
        commitCandidate match {
          case Some(coll) => context.broadcastIncludingMyself(Message.Commit(id, context.iteration, commitCandidate.get))
          case None => output("commit-candidate-not-available", "proposal was missing")
        }

      case Round.Notify =>
        if (lastLocallyFormedCommitCertificate.isDefined)
          context.broadcastIncludingMyself(Message.Notify(id, context.iteration, lastLocallyFormedCommitCertificate.get))
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
        return None

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
          localStatistics.emptyProposalRounds += 1
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
        //todo: we can optimize the logic of Notify round by taking into account
        //todo: also a locally-formed commit certificate (if present)
        //todo: this could be accomplished by adding do this collection yet another 'notify' message coming from myself
        val allNotifyMessages = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Notify]]

        //update cached certificates
        for (msg <- allNotifyMessages) {
          val certificate = msg.commitCertificate
          val map: mutable.HashMap[CollectionOfMarbles, CommitCertificate] = certificates.get(certificate.iteration) match {
            case Some(m) => m
            case None =>
              val newMap = new mutable.HashMap[CollectionOfMarbles, CommitCertificate]
              certificates += certificate.iteration -> newMap
              newMap
          }

          if (! map.contains(certificate.acceptedSet))
            map += certificate.acceptedSet -> certificate
        }

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
          val oldSet: Set[Int] = currentConsensusApproximation.elements
          val newSet: Set[Int] = goodNotifyMsg.commitCertificate.acceptedSet.elements
          val overrideCase = NotifyRoundOverrideCase.recognize(oldSet, newSet)
          overrideCase match {
            case NotifyRoundOverrideCase.NoChange => //ignore
            case NotifyRoundOverrideCase.GoingUp => localStatistics.notifyCertificateOverridesWithSetGoingUp += 1
            case NotifyRoundOverrideCase.GoingDown => localStatistics.notifyCertificateOverridesWithSetGoingDown += 1
            case NotifyRoundOverrideCase.NonMonotonic => localStatistics.notifyCertificateOverridesWithNonMonotonicChange += 1
          }
          if (certifiedIteration >= 0 && this.isOutputEnabled) {
            val iterUpdateDesc = s"$certifiedIteration->${goodNotifyMsg.commitCertificate.iteration}"
            val differenceDesc: String = overrideCase match {
              case NotifyRoundOverrideCase.NoChange => "no change"
              case NotifyRoundOverrideCase.GoingUp => s"added ${newSet diff oldSet}"
              case NotifyRoundOverrideCase.GoingDown => s"removed ${oldSet diff newSet}"
              case NotifyRoundOverrideCase.NonMonotonic => s"added ${newSet diff oldSet} removed ${oldSet diff newSet}"
            }
            output("better-commit-certificate", s"certified iteration update: $iterUpdateDesc difference: $differenceDesc")
          }
          currentConsensusApproximation = goodNotifyMsg.commitCertificate.acceptedSet
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

  private def isStatusMessageJustified(msg: Message.Status): Boolean =
    if (msg.certifiedIteration == -1) {
      msg.acceptedSet.elements.subsetOf(marblesWithEnoughSupport)
    } else {
      val mapOption: Option[mutable.HashMap[CollectionOfMarbles, CommitCertificate]] = certificates.get(msg.certifiedIteration)
      mapOption match {
        case None => false
        case Some(m) => m.contains(msg.acceptedSet)
      }
    }

}
