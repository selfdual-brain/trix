package com.selfdualbrain.trix.turns_based_engine.nodes

import com.selfdualbrain.continuum.data_structures.FastIntMap
import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.cryptography.Hash
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model._
import com.selfdualbrain.trix.turns_based_engine._

import scala.collection.mutable

/**
 * We follow the math paper on Hare, with the following exceptions:
 * - we persistently blacklist all discovered equivocators
 * - safe value proofs are built following the go-spacemesh logic
 * - termination is delayed for N iterations, so to avoid the self-lock problem (aka "zombie iterations")
 */
class HonestNodeWithZombieRounds(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles, out: Option[AbstractTextOutput])
  extends Node(id, simConfig, context, inputSet, out) {

  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]
  private var certifiedIteration: Int = -1
  private var currentConsensusApproximation: CollectionOfMarbles = inputSet
  private var commitCandidate: Option[CollectionOfMarbles] = None
  private var preroundMessagesSnapshot: Option[Iterable[Message]] = None
  private var marblesWithEnoughSupport: Set[Marble] = Set.empty
  private var latestValidStatusMessages: Set[Message.Status] = Set.empty
  private var lastLocallyFormedCommitCertificate: Option[CommitCertificate] = None
  private var notifyMessagesCounter = new mutable.HashMap[CollectionOfMarbles, mutable.HashSet[NodeId]]
  private var notifyMsgSenders = new mutable.HashSet[NodeId](simConfig.averageNumberOfActiveNodes.toInt * 10, 0.75)
  private var votesMap = new mutable.HashMap[NodeId, CollectionOfMarbles](simConfig.averageNumberOfActiveNodes.toInt * 10, 0.75)
  //iteration ----> map[collectionOfMarbles ---> certificate]
  private val certificates = new FastIntMap[mutable.HashMap[CollectionOfMarbles, CommitCertificate]](100)
  private val localStatistics = new LocalNodeStats

  private class LocalNodeStats extends NodeStats {
    var notifyCertificateOverridesWithSetGoingUp: Int = 0
    var notifyCertificateOverridesWithSetGoingDown: Int = 0
    var notifyCertificateOverridesWithNonMonotonicChange: Int = 0
    var emptyProposalRounds: Int = 0
    override def equivocatorsDiscovered: Int = equivocators.size
  }
  private var readyToTerminate: Boolean = false
  private var zombieIteration: Int = 0

  override def stats: NodeStats = localStatistics

  override def onIterationBegin(iteration: Int): Unit = {
    if (readyToTerminate)
      zombieIteration += 1

    if (simConfig.resetNotificationsCounterAtEveryIteration) {
      notifyMessagesCounter = new mutable.HashMap[CollectionOfMarbles, mutable.HashSet[NodeId]]
      notifyMsgSenders = new mutable.HashSet[NodeId](simConfig.averageNumberOfActiveNodes.toInt * 2, 0.75)
      votesMap = new mutable.HashMap[NodeId, CollectionOfMarbles](simConfig.averageNumberOfActiveNodes.toInt * 2, 0.75)
    }
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
          context.broadcastIncludingMyself(Message.Proposal(id, context.iteration, svp.get, context.rng.nextLong(), Hash.random(context.rng)))
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
        //todo: this could be accomplished by adding to this collection yet another 'notify' message coming from myself
        val allNotifyMessages = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.Notify]]
        val effectiveNotifyMessages = filterOutNotifyOverrides(allNotifyMessages)

        //update cached certificates
        for (msg <- effectiveNotifyMessages) {
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

        val notifyMessagesWithGreaterCertifiedIteration = effectiveNotifyMessages.filter(msg => msg.commitCertificate.iteration >= certifiedIteration)

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

        //updating votes counter
        for (msg <- effectiveNotifyMessages) {
          notifyMsgSenders += msg.sender
          val setInQuestion: CollectionOfMarbles = msg.commitCertificate.acceptedSet

          if (! simConfig.ignoreSecondNotifyFromTheSameSender) {
            votesMap.get(msg.sender) match {
              case None => //ignore
              case Some(previouslyVotedFor) =>
                notifyMessagesCounter(previouslyVotedFor).remove(msg.sender)
            }
            votesMap += msg.sender -> setInQuestion
          }

          notifyMessagesCounter.get(setInQuestion) match {
            case None =>
              val coll = new mutable.HashSet[NodeId]
              coll += msg.sender
              notifyMessagesCounter += setInQuestion -> coll

            case Some(coll) =>
              coll += msg.sender
          }
        }

        output("notify-counters", notifyMessagesCounterPrettyPrint())

        //finding the set of marbles with most votes
        var topCandidate: CollectionOfMarbles = CollectionOfMarbles.empty
        var topNumberOfVotes: Int = 0
        var numberOfTopCandidates: Int = 0
        for ((candidate,votes) <- notifyMessagesCounter) {
          if (votes.size > topNumberOfVotes) {
            topCandidate = candidate
            topNumberOfVotes = votes.size
            numberOfTopCandidates = 1
          } else if (votes.size == topNumberOfVotes) {
            numberOfTopCandidates += 1
          }
        }

        if (topNumberOfVotes > simConfig.faultyNodesTolerance + 1)
          consensusResult = Some(topCandidate)

        if (consensusResult.nonEmpty) {
          readyToTerminate = true
          if (zombieIteration >= simConfig.zombieIterationsLimit) {
            if (numberOfTopCandidates > 1)
              throw new RuntimeException(s"Ambiguity during consensus result. Number of top candidates = $numberOfTopCandidates")
            output("terminating", s"consensus=${consensusResult.get}")
            return consensusResult
          }
          output(s"zombie-phase[$zombieIteration]", s"consensus-candidate: ${consensusResult.get}")
        }
        return None
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

  private def filterOutNotifyOverrides(messages: Iterable[Message.Notify]): Iterable[Message.Notify] = {
    if (simConfig.ignoreSecondNotifyFromTheSameSender)
      messages.filter(msg => ! notifyMsgSenders.contains(msg.sender))
    else
      messages
  }

}
