package com.selfdualbrain.trix.event_based_engine

import com.selfdualbrain.continuum.time.TimeDelta
import com.selfdualbrain.trix.protocol_model._
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * An implementation of validator that follows fully-synchronous approach of the original protocol spec.
 * This is a honest validator implementation (= no malicious behaviour)
 */
class HonestFullySynchronousValidator(
                                       id: NodeId,
                                       node: P2PNode,
                                       roundLength: TimeDelta,
                                       val inputSet: CollectionOfMarbles
                                     ) extends Validator {
  private val log = LoggerFactory.getLogger(s"validator-$id")

  private var iteration: Int = 0
  private var currentRound: Option[Round] = None
  private var certifiedIteration: Int = -1
  private var commitCandidate: Option[CollectionOfMarbles] = None
  private var lateMessagesCount: Int = 0
  private var earlyMessagesBuffer: mutable.Buffer[Message] = new ListBuffer[Message]
  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]

  override def validatorId: NodeId = id

  override def nodeId: P2PNode = node

  override def startup(): Unit = {
    log.debug("startup")
  }

  override def onNewMessageArrived(msg: Message): Unit = ???

  override def onRoundStarted(iteration: Marble, round: Round): Unit = ???
}
