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
  private var terminatedNodesCounter: Int = 0

  /*                                                 PUBLIC                                             */

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

  override def nodesWhichTerminated(): Iterator[NodeId] =
    nodeBoxes.iterator.filter(box => box.context.reachedTerminationOfProtocol).map(box => box.node.id)

  override def numberOfNodesWhichTerminated(): NodeId = terminatedNodesCounter

  /*                                                 NODE CONTEXT                                              */

  class NodeContextImpl(nodeId: NodeId) extends NodeContext {
    private var terminatedFlag: Boolean = false

    override def iteration: NodeId = SimEngineImpl.this.currentIteration
    override def currentRound: Round =
      SimEngineImpl.this.currentRound match {
        case Some(r) => r
        case None =>
          throw new RuntimeException(s"trying to access node context outside of round - node $nodeId")
      }

    override def amIActiveInCurrentRound: Boolean = currentRoundProcess.get.roleDistributionOracle.isNodeActive(nodeId)

    override def broadcast(msg: Message): Unit = {
      currentRoundProcess.get.registerMsgBroadcast(msg)
    }

    override def send(msg: Message, destination: NodeId): Unit = {
      currentRoundProcess.get.registerMsgSend(msg, destination)
    }

    override def inbox(): Iterable[Message] = {
      val messagesCollection = currentRoundProcess.get.getAllMessagesDeliveredTo(nodeId)
      if (config.isNetworkReliable)
        return messagesCollection
      else
        return messagesCollection.filter(msg => msgDeliveryRng.nextDouble() > config.probabilityOfAMessageGettingLost)
    }

    override def signalProtocolTermination(): Unit = {
      terminatedFlag = true
      terminatedNodesCounter += 1
    }

    def reachedTerminationOfProtocol: Boolean = terminatedFlag
  }

  /*                                                 SINGLE ROUND PROCESSING                                          */

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

    def registerMsgBroadcast(msg: Message): Unit = {
      broadcastBuffer += msg
    }

    def registerMsgSend(msg: Message, destination: NodeId): Unit = {
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
      for (i <- 0 until config.numberOfNodes if roleDistributionOracle.isNodeActive(i)) {
        val box = nodeBoxes(i)
        if (! box.context.reachedTerminationOfProtocol)
          box.node.executeSendingPhase()
      }

      //run receiving phase of this round
      for (i <- 0 until config.numberOfNodes) {
        val box = nodeBoxes(i)
        if (! box.context.reachedTerminationOfProtocol)
          nodeBoxes(i).node.executeCalculationPhase()
      }
    }
  }

  case class NodeBox(node: Node, context: NodeContextImpl)

  /*                                                 INITIALIZATION OF NODES                                          */

  def initializeNodes(): Array[NodeBox] = {
    val result = new Array[NodeBox](config.numberOfNodes)

    val numberOfHonestNodes: Int = config.numberOfNodes - config.actualNumberOfFaultyNodes
    val numberOfDeafNodes: Int = math.floor(config.actualNumberOfFaultyNodes * config.fractionOfDeafNodesAmongMalicious).toInt
    val inputSetsGenerator = new InputSetsGenerator(config)
    val inputSetsConfiguration = inputSetsGenerator.generate()

    var lastNodeId: Int = -1

    for (i <- 1 to numberOfHonestNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new HonestNode(lastNodeId, config, context, inputSetsConfiguration.inputSetFor(i))
      val box = NodeBox(node, context)
      result(lastNodeId) = box
    }

    for (i <- 1 to config.actualNumberOfFaultyNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new GangNode(lastNodeId, config, context, inputSetsConfiguration.inputSetFor(i))
      val box = NodeBox(node, context)
      result(lastNodeId) = box
    }

    for (i <- 1 to numberOfDeafNodes) {
      lastNodeId += 1
      val context = new NodeContextImpl(lastNodeId)
      val node = new DeafNode(lastNodeId, config, context, inputSetsConfiguration.inputSetFor(i))
      val box = NodeBox(node, context)
      result(lastNodeId) = box
    }

    return result
  }

}
