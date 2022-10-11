package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{NodeId, Round}

import scala.collection.mutable

class SimEngineImpl(config: Config) extends SimEngine {
  private val nodeBoxes: Map[NodeId, NodeBox] = initializeNodes()
  private var currentRound: Option[Round] = None
  private var currentIteration: Int = 0

  override def playNextRound(): Unit = ???

  class NodeContextImpl(nodeId: NodeId) extends NodeContext {
    override def iteration: NodeId = SimEngineImpl.this.currentIteration
    override def currentRound: Option[Round] = SimEngineImpl.this.currentRound
  }

  case class NodeBox(node: Node, context: NodeContextImpl)

  def initializeNodes(): Map[NodeId, NodeBox] = {
    val result = new mutable.HashMap[NodeId, NodeBox]

    val numberOfMaliciousNodes: Int = math.floor(config.numberOfNodes * config.maxFractionOfMaliciousNodes).toInt
    val numberOfHonestNodes: Int = config.numberOfNodes - numberOfMaliciousNodes
    val numberOfDeafNodes: Int = math.floor(numberOfMaliciousNodes * config.fractionOfDeafNodesAmongMalicious).toInt

    var lastNodeId: Int = -1

    for (i <- 1 to numberOfHonestNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new HonestNode(lastNodeId, config, context)
      val box = NodeBox(node, context)
      result += lastNodeId -> box
    }

    for (i <- 1 to numberOfMaliciousNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new GangNode(lastNodeId, config, context)
      val box = NodeBox(node, context)
      result += lastNodeId -> box
    }

    for (i <- 1 to numberOfDeafNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new DeafNode(lastNodeId, config, context)
      val box = NodeBox(node, context)
      result += lastNodeId -> box
    }

    return result.toMap
  }

  override def nodesWhichTerminated(): Set[NodeId] = ???
}
