package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{Message, NodeId, Round}
import org.apache.commons.math3.random.MersenneTwister

import scala.collection.mutable.ArrayBuffer

class SimEngineImpl(config: Config) extends SimEngine {
  private val nodeBoxes: Array[NodeBox] = initializeNodes()
  private var currentRound: Option[Round] = None
  private var currentIteration: Int = 0
  private val eligibilityMasterRng = new MersenneTwister(config.eligibilityRngMasterSeed)
  private val msgDeliveryRng = new MersenneTwister(config.msgDeliveryRngSeed)
  private val currentRoundProcess: Option[SingleRoundProcess] = None

  override def playNextRound(): Unit = {
    //update iteration and round
    currentRound match {
      case None => currentRound =
        Some(Round.Preround)
      case Some(Round.Preround) =>
        currentRound = Some(Round.Status)
      case Some(Round.Status) =>
        currentRound = Some(Round.Proposal)
      case Some(Round.Proposal) =>
        currentRound = Some(Round.Commit)
      case Some(Round.Commit) =>
        currentRound = Some(Round.Notify)
      case Some(Round.Notify) =>
        currentRound = Some(Round.Status)
        currentIteration += 1
    }

    val roundProcess = new SingleRoundProcess(
      iteration = currentIteration,
      round = currentRound.get,
      salt = eligibilityMasterRng.nextInt()
    )
    roundProcess.run()
  }

  class NodeContextImpl(nodeId: NodeId) extends NodeContext {
    override def iteration: NodeId = SimEngineImpl.this.currentIteration
    override def currentRound: Option[Round] = SimEngineImpl.this.currentRound

    override def isActiveInCurrentRound: Boolean = currentRoundProcess.get.roleDistributionOracle.isActive(nodeId)

    override def broadcast(msg: Message): Unit = {
      currentRoundProcess.get.registerBroadcast(msg)
    }

    override def send(msg: Message, destination: NodeId): Unit = {
      currentRoundProcess.get.registerSend(msg, destination)
    }

    override def inbox(): Iterable[Message] = {
      val messagesCollection = currentRoundProcess.get.getAllMessagesDeliveredTo(nodeId)
      if (config.isNetworkReliable)
        return messagesCollection
      else
        return messagesCollection.filter(msg => msgDeliveryRng.nextDouble() > config.probabilityOfAMessageGettingLost)
    }

  }

  class SingleRoundProcess(iteration: Int, round: Round, salt: Int) {
    //perform selection of nodes to be active in this round
    val perRoundEligibilitySeed: Long = salt.toLong * (iteration * 4 + round.number + 1)
    val electedSubsetAverageSize: Double =
      if (round.isLeaderRound)
        config.averageNumberOfLeaders
      else
        config.averageNumberOfActiveNodes
    val roleDistributionOracle: RoleDistributionOracle = new CachingRoleDistributionOracle(config.numberOfNodes, electedSubsetAverageSize, rngSeed = perRoundEligibilitySeed)
    private val broadcastBuffer = new ArrayBuffer[Message]((electedSubsetAverageSize * 2).toInt)
    private val inboxes = new Array[Option[ArrayBuffer[Message]]](config.numberOfNodes)

    def registerBroadcast(msg: Message): Unit = {
      broadcastBuffer += msg
    }

    def registerSend(msg: Message, destination: NodeId): Unit = {
      inboxes(destination) match {
        case None =>
          inboxes(destination) = Some(new ArrayBuffer[Message](config.initialSizeOfInboxBuffer))
        case other =>
          //ignore
      }

      inboxes(destination).get += msg
    }

    def getAllMessagesDeliveredTo(nodeId: NodeId): Iterable[Message] = {
      val inboxPart: Iterable[Message] = inboxes(nodeId) match {
        case Some(buf) => buf
        case None => Iterable.empty[Message]
      }

      return inboxPart ++ broadcastBuffer
    }

    def run(): Unit = {
      //run sending phase of this round
      for (i <- 0 until config.numberOfNodes if roleDistributionOracle.isActive(i))
        nodeBoxes(i).node.executeSendingPhase()

      //run receiving phase of this round
      for (i <- 0 until config.numberOfNodes)
        nodeBoxes(i).node.executeCalculationPhase()
    }
  }

  case class NodeBox(node: Node, context: NodeContextImpl)

  def initializeNodes(): Array[NodeBox] = {
    val result = new Array[NodeBox](config.numberOfNodes)

    val numberOfMaliciousNodes: Int = math.floor(config.numberOfNodes * config.maxFractionOfMaliciousNodes).toInt
    val numberOfHonestNodes: Int = config.numberOfNodes - numberOfMaliciousNodes
    val numberOfDeafNodes: Int = math.floor(numberOfMaliciousNodes * config.fractionOfDeafNodesAmongMalicious).toInt

    var lastNodeId: Int = -1

    for (i <- 1 to numberOfHonestNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new HonestNode(lastNodeId, config, context)
      val box = NodeBox(node, context)
      result(lastNodeId) = box
    }

    for (i <- 1 to numberOfMaliciousNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new GangNode(lastNodeId, config, context)
      val box = NodeBox(node, context)
      result(lastNodeId) = box
    }

    for (i <- 1 to numberOfDeafNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new DeafNode(lastNodeId, config, context)
      val box = NodeBox(node, context)
      result(lastNodeId) = box
    }

    return result
  }

  override def nodesWhichTerminated(): Set[NodeId] = ???
}
