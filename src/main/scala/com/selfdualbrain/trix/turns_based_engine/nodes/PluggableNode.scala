package com.selfdualbrain.trix.turns_based_engine.nodes

import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}
import com.selfdualbrain.trix.turns_based_engine.nodes.PluggableNode.RoundOrchestrator
import com.selfdualbrain.trix.turns_based_engine.{Config, Node, NodeContext, NodeStats}

import scala.collection.mutable

abstract class PluggableNode(id: NodeId, simConfig: Config, context: NodeContext, inputSet: CollectionOfMarbles, out: Option[AbstractTextOutput])
  extends Node(id, simConfig, context, inputSet, out) {

  private val equivocators: mutable.Set[NodeId] = new mutable.HashSet[NodeId]
  private var certifiedIteration: Int = -1
  private var currentConsensusApproximation: CollectionOfMarbles = inputSet
  private val localStatistics = new LocalNodeStats
  private val preroundHandler: RoundOrchestrator = createPreroundHandler()
  private val statusRoundHandler: RoundOrchestrator = createStatusRoundHandler()
  private val proposalRoundHandler: RoundOrchestrator = createProposalRoundHandler()
  private val commitRoundHandler: RoundOrchestrator = createCommitRoundHandler()
  private val notifyRoundHandler: RoundOrchestrator = createNotifyRoundHandler()

  class LocalNodeStats extends NodeStats {
    var notifyCertificateOverridesWithSetGoingUp: Int = 0
    var notifyCertificateOverridesWithSetGoingDown: Int = 0
    var notifyCertificateOverridesWithNonMonotonicChange: Int = 0
    var emptyProposalRounds: Int = 0
    override def equivocatorsDiscovered: Int = equivocators.size
  }

  protected def createPreroundHandler(): RoundOrchestrator
  protected def createStatusRoundHandler(): RoundOrchestrator
  protected def createProposalRoundHandler(): RoundOrchestrator
  protected def createCommitRoundHandler(): RoundOrchestrator
  protected def createNotifyRoundHandler(): RoundOrchestrator

}

object PluggableNode {

  abstract class RoundOrchestrator(nodeId: NodeId, out: Option[AbstractTextOutput]) {
    def onIterationBegin(iteration: Int): Unit

    def executeSendingPhase(): Unit

    //returns Some(consensusResult) if protocol termination was achieved
    def executeCalculationPhase(): Option[CollectionOfMarbles]

    protected def output(code: String, body: String): Unit = {
      if (out.isDefined)
        out.get.print(s"$nodeId:$code:$body")
    }

    protected def isOutputEnabled: Boolean = out.isDefined

  }

}
