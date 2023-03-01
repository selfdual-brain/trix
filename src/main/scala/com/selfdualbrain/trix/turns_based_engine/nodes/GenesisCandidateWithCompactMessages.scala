package com.selfdualbrain.trix.turns_based_engine.nodes

import com.selfdualbrain.continuum.data_structures.FastIntMap
import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.cryptography.{CryptographicDigester, Hash, RealSha256Digester}
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model._
import com.selfdualbrain.trix.turns_based_engine._

import scala.collection.mutable

/**
 * We follow the math paper on Hare, with the following exceptions:
 * - we persistently blacklist all discovered equivocators
 * - "f+1" rule is replaced with Lisbon-style "f-k+1" rule
 * - safe value proofs are built following the go-spacemesh logic
 * - termination is delayed for N iterations, so to avoid the self-lock problem (aka "zombie iterations")
 *
 * Caution: this is not a complete Lisbon design - missing are fraud proofs and the smart gossip part. The goal here is
 * to prototype the "f-k+1" rule processing.
 *
 * This version includes data volume optimizations proposed by Dmitry on 2023-02-27
 */
class GenesisCandidateWithCompactMessages(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles, out: Option[AbstractTextOutput])
  extends Node(id, simConfig, context, inputSet, out) {

  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]
  private var certifiedIteration: Int = -1
  private var currentConsensusApproximation: CollectionOfMarbles = inputSet
  private var commitCandidate: Option[CollectionOfMarbles] = None
  private var commitCandidateHash: Option[Hash] = None
  private var preroundMessagesSnapshot: Option[Iterable[Message]] = None
  private var marblesWithEnoughSupport: Set[Marble] = Set.empty
  private var latestValidStatusMessages: Set[Message.Status] = Set.empty
  private var lastLocallyFormedCommitCertificate: Option[CompactCommitCertificate] = None

  //iteration ----> map[collectionOfMarbles ---> certificate]
  private val certificates = new FastIntMap[mutable.HashMap[CollectionOfMarbles, CompactCommitCertificate]](100)
  private val localStatistics = new LocalNodeStats

  private class LocalNodeStats extends NodeStats {
    var notifyCertificateOverridesWithSetGoingUp: Int = 0
    var notifyCertificateOverridesWithSetGoingDown: Int = 0
    var notifyCertificateOverridesWithNonMonotonicChange: Int = 0
    var emptyProposalRounds: Int = 0
    override def equivocatorsDiscovered: Int = equivocators.size
  }

  override def onIterationBegin(iteration: Marble): Unit = {
    //do nothing
  }

  override def stats: NodeStats = localStatistics

  override def executeSendingPhase(): Unit = {

    context.currentRound match {
      case Round.Preround =>
        val msg = Message.Preround(
          sender = id,
          inputSet = inputSet,
          eligibilityProof = context.rng.nextLong(),
          signature = Hash.random(context.rng)
        )
        context.broadcastIncludingMyself(msg)

      case Round.Status =>
        val msg = Message.Status(
          sender = id,
          iteration = context.iteration,
          certifiedIteration = certifiedIteration,
          acceptedSet = currentConsensusApproximation,
          eligibilityProof = context.rng.nextLong(),
          signature = Hash.random(context.rng)
        )
        context.broadcastIncludingMyself(msg)

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
          val msg = Message.Proposal(
            sender = id,
            iteration = context.iteration,
            safeValueProof = svp.get,
            eligibilityProof = context.rng.nextLong(),
            signature = Hash.random(context.rng)
          )
          context.broadcastIncludingMyself(msg)
        }

      case Round.Commit =>
        commitCandidate match {
          case Some(coll) =>
            val msg: Message.CompactCommit = Message.CompactCommit(
              sender = id,
              iteration = context.iteration,
              commitCandidateHash = commitCandidateHash.get,
              eligibilityProof = context.rng.nextLong(),
              signature = Hash.random(context.rng)
            )
            context.broadcastIncludingMyself(msg)
          case None =>
            output("commit-candidate-not-available", "proposal was missing")
        }

      case Round.Notify =>
        if (lastLocallyFormedCommitCertificate.isDefined) {
          val msg = Message.CompactNotify(
            sender = id,
            iteration = context.iteration,
            commitCertificate = lastLocallyFormedCommitCertificate.get,
            eligibilityProof = context.rng.nextLong(),
            signature = Hash.random(context.rng)
          )
          context.broadcastIncludingMyself(msg)
        }
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

          //validation of svp
          val svp: SafeValueProof = bestMsgSoFar.safeValueProof
          if (isIncomingSvpValid(svp)) {
            output("accepting-winning-proposal", bestMsgSoFar.toString)
            commitCandidate = Some(bestMsgSoFar.safeValueProof.safeValue)
            val digester: CryptographicDigester = new RealSha256Digester
            for (element <- commitCandidate.get)
              digester.field(element)
            commitCandidateHash = Some(digester.generateHash())
          } else {
            //            output(code ="invalid-svp", bestMsgSoFar.toString)
            commitCandidate = None
            commitCandidateHash = None
          }
        } else {
          output("proposal-is-missing", "")
          localStatistics.emptyProposalRounds += 1
          commitCandidate = None
          commitCandidateHash = None
        }

        return None

      case Round.Commit =>
        if (commitCandidate.isDefined) {
          val allCommitMessages = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.CompactCommit]]
          val commitMessagesVotingOnOurCandidate = allCommitMessages.filter(msg => msg.commitCandidateHash == commitCandidateHash.get)
          val howManyOfThem = commitMessagesVotingOnOurCandidate.size
          if (howManyOfThem >= simConfig.faultyNodesTolerance + 1) {
            val squeezedCommitMessages = commitMessagesVotingOnOurCandidate.toArray.map(msg => SqueezedCommitInfo(msg.sender, msg.eligibilityProof, msg.signature))
            lastLocallyFormedCommitCertificate = Some(CompactCommitCertificate(
              acceptedSet = commitCandidate.get,
              context.iteration,
              commitMessages = squeezedCommitMessages))
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
        val effectiveNotifyMessages: Iterable[Message.CompactNotify] = filterOutEquivocationsAndDuplicates(context.inbox()).asInstanceOf[Iterable[Message.CompactNotify]]

        //update cached certificates
        for (msg <- effectiveNotifyMessages) {
          val certificate = msg.commitCertificate
          val map: mutable.HashMap[CollectionOfMarbles, CompactCommitCertificate] = certificates.get(certificate.iteration) match {
            case Some(m) => m
            case None =>
              val newMap = new mutable.HashMap[CollectionOfMarbles, CompactCommitCertificate]
              certificates += certificate.iteration -> newMap
              newMap
          }

          if (! map.contains(certificate.acceptedSet))
            map += certificate.acceptedSet -> certificate
        }

        if (commitCandidate.isDefined) {
          val compatibleNotifyMessages = effectiveNotifyMessages.filter(
            msg => msg.commitCertificate.acceptedSet == commitCandidate.get && msg.commitCertificate.iteration >= certifiedIteration
          )
          //we update current consensus approximation once we get a notify message with a better certificate than last we knew about
          if (compatibleNotifyMessages.nonEmpty) {
            val goodNotifyMsg = compatibleNotifyMessages.head
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

          if (compatibleNotifyMessages.size >= simConfig.faultyNodesTolerance + 1) {
            output("terminating", s"consensus=${currentConsensusApproximation}")
            return Some(currentConsensusApproximation)
          }
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

  private def isStatusMessageJustified(msg: Message.Status): Boolean =
    if (msg.certifiedIteration == -1) {
      msg.acceptedSet.elements.subsetOf(marblesWithEnoughSupport)
    } else {
      val mapOption: Option[mutable.HashMap[CollectionOfMarbles, CompactCommitCertificate]] = certificates.get(msg.certifiedIteration)
      mapOption match {
        case None => false
        case Some(m) => m.contains(msg.acceptedSet)
      }
    }

  def isIncomingSvpValid(svp: SafeValueProof): Boolean = {
    svp match {
      case SafeValueProof.Bootstrap(iteration, statusMessages, magmaSet) =>
        if (iteration != context.iteration) {
          output(code ="invalid-svp", "bootstrap case: wrong svp iteration")
          return false
        }

        for (msg <- statusMessages) {
          if (! msg.isSignatureOK) {
            output(code = "invalid-svp", "bootstrap case: wrong signature")
            return false
          }
          if (! msg.isEligibilityProofOK) {
            output(code = "invalid-svp", "bootstrap case: wrong eligibility")
            return false
          }
          if (msg.iteration != context.iteration) {
            output(code = "invalid-svp", s"bootstrap case: wrong iteration of status message $msg")
            return false
          }
          if (msg.certifiedIteration != -1) {
            output(code = "invalid-svp", s"bootstrap case: wrong certified iteration (should be -1) of status message $msg")
            return false
          }
          if (! isStatusMessageJustified(msg)) {
            output(code = "invalid-svp", s"bootstrap case: status message did not pass local validation: $msg")
            return false
          }
        }

        val bufferOfMarbles = new mutable.HashSet[Marble]
        for (msg <- statusMessages)
          bufferOfMarbles.addAll(msg.acceptedSet.elements)
        val recalculatedMagmaSet = new CollectionOfMarbles(bufferOfMarbles.toSet)

        if (! recalculatedMagmaSet.equals(magmaSet)) {
          output(code = "invalid-svp", "bootstrap case: declared magma set does not match cited collection of messages")
          return false
        }

        return true

      case SafeValueProof.Proper(iteration, statusMessages, magicMessage) =>
        if (iteration != context.iteration) {
          output(code ="invalid-svp", "proper case: wrong svp iteration")
          return false
        }

        for (msg <- statusMessages) {
          if (! msg.isSignatureOK) {
            output(code = "invalid-svp", "proper case: wrong signature")
            return false
          }
          if (! msg.isEligibilityProofOK) {
            output(code = "invalid-svp", "proper case: wrong eligibility")
            return false
          }
          if (msg.iteration != context.iteration) {
            output(code = "invalid-svp", s"proper case: wrong iteration of status message $msg")
            return false
          }

          if (! isStatusMessageJustified(msg)) {
            output(code = "invalid-svp", s"proper case: status message did not pass local validation: $msg")
            return false
          }
        }

        val maxCertifiedIteration: Int = statusMessages.map(msg => msg.certifiedIteration).max
        val messagesWithMaxCertifiedIteration = statusMessages.filter(msg => msg.certifiedIteration == maxCertifiedIteration)
        val candidateSets = messagesWithMaxCertifiedIteration.map(msg => msg.acceptedSet)
        if (candidateSets.size > 1)
          return false

        if (magicMessage.acceptedSet != candidateSets.head)
          return false

        return true
    }
  }


}
