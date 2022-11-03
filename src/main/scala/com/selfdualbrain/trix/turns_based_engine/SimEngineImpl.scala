package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.data_structures.IndexedBatteryOfIntCounters
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, Message, NodeId, Round}

import scala.collection.mutable.ArrayBuffer

class SimEngineImpl(
                     config: Config,
                     eligibilityRng: RandomNumberGenerator,
                     msgDeliveryRng: RandomNumberGenerator,
                     nodeDecisionsRng: RandomNumberGenerator,
                     inputSetsGenerator: InputSetsGenerator,
                     out: Option[AbstractTextOutput]
                   ) extends SimEngine {

  private val nodeBoxes: Array[NodeBox] = initializeNodes()
  var currentRound: Option[Round] = None
  var currentIteration: Int = 0
  private var currentRoundProcess: Option[SingleRoundProcess] = None
  private var terminatedNodesCounter: Int = 0
  private var counterOfRoundsWithTerminatingNodes: Int = 0
  private var allInterNodeMessagesCounter: Int = 0
  private var lostMessagesCounter: Int = 0

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

    val initialNumberOfTerminatedNodes: Int = terminatedNodesCounter
    val process = new SingleRoundProcess(
      iteration = currentIteration,
      round = currentRound.get,
      rng = eligibilityRng
    )
    currentRoundProcess = Some(process)
    process.run()
    val finalNumberOfTerminatedNodes: Int = terminatedNodesCounter

    if (finalNumberOfTerminatedNodes > initialNumberOfTerminatedNodes)
      counterOfRoundsWithTerminatingNodes += 1
  }

  override def nodesWhichTerminated: Iterator[NodeId] =
    nodeBoxes.iterator.filter(box => box.context.reachedTerminationOfProtocol).map(box => box.node.id)

  override def numberOfNodesWhichTerminated: NodeId = terminatedNodesCounter

  override def reachedTerminationOfProtocol(nodeId: NodeId): Boolean = {
    require (nodeId >= 0 && nodeId < config.numberOfNodes, s"node id=$nodeId outside range of this engine: 0..${config.numberOfNodes-1}")
    return nodeBoxes(nodeId).context.reachedTerminationOfProtocol
  }

  override def consensusResult(nodeId: NodeId): Option[CollectionOfMarbles] = {
    require (nodeId >= 0 && nodeId < config.numberOfNodes, s"node id=$nodeId outside range of this engine: 0..${config.numberOfNodes-1}")
    return nodeBoxes(nodeId).context.consensusResult
  }

  override def numberOfRoundsWithTermination: NodeId = counterOfRoundsWithTerminatingNodes

  override def measuredLostMessagesFraction: Double = lostMessagesCounter.toDouble / allInterNodeMessagesCounter

  override def nodeStats(nodeId: NodeId): NodeStats = nodeBoxes(nodeId).node.stats

  /*                                                 NODE CONTEXT                                              */


  class NodeContextImpl(nodeId: NodeId) extends NodeContext {
    private var terminatedFlag: Boolean = false
    private var consensusResultX: Option[CollectionOfMarbles] = None

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

    override def broadcastIncludingMyself(msg: Message): Unit = {
      currentRoundProcess.get.registerMsgBroadcast(msg)
    }

    override def send(msg: Message, destination: NodeId): Unit = {
      currentRoundProcess.get.registerMsgSend(msg, destination)
    }

    override def inbox(): Iterable[Message] = {
      val messagesCollection = currentRoundProcess.get.getAllMessagesDeliveredTo(nodeId)
      allInterNodeMessagesCounter += messagesCollection.count(msg => msg.sender != nodeId)
      if (config.isNetworkReliable)
        return messagesCollection
      else
        return messagesCollection.filter(msg => {
          val shouldBeLost: Boolean = {
            if (nodeId == msg.sender)
              false
            else
              msgDeliveryRng.nextDouble() < config.probabilityOfAMessageGettingLost
          }
          if (shouldBeLost)
            lostMessagesCounter += 1
          !shouldBeLost
        })
    }

    override def signalProtocolTermination(coll: CollectionOfMarbles): Unit = {
      terminatedFlag = true
      terminatedNodesCounter += 1
      consensusResultX = Some(coll)
    }

    override def consensusResult: Option[CollectionOfMarbles] = consensusResultX

    def reachedTerminationOfProtocol: Boolean = terminatedFlag

    override def rng: RandomNumberGenerator = nodeDecisionsRng
  }

  /*                                                 SINGLE ROUND PROCESSING                                          */

  class SingleRoundProcess(iteration: Int, round: Round, rng: RandomNumberGenerator) {
    //perform selection of nodes to be active in this round
    val electedSubsetAverageSize: Double =
      if (round.isLeaderRound)
        config.averageNumberOfLeaders
      else
        config.averageNumberOfActiveNodes

    val roleDistributionOracle: RoleDistributionOracle =
      new CachingRoleDistributionOracle(config.numberOfNodes, electedSubsetAverageSize, config.enforceFixedNumberOfActiveNodes, rng)

    private val broadcastBuffer = new ArrayBuffer[Message]((electedSubsetAverageSize * 2).toInt)
    private val inboxes = new Array[Option[ArrayBuffer[Message]]](config.numberOfNodes)
    for (i <- inboxes.indices)
      inboxes(i) = None

    def registerMsgBroadcast(msg: Message): Unit = {
      broadcastBuffer += msg
      output(s"${msg.sender}:broadcast:$msg")
    }

    def registerMsgSend(msg: Message, destination: NodeId): Unit = {
      inboxes(destination) match {
        case None =>
          inboxes(destination) = Some(new ArrayBuffer[Message](config.initialSizeOfInboxBuffer))
        case other =>
          //ignore
      }

      inboxes(destination).get += msg
      output(s"${msg.sender}:direct-send-to[$destination]:$msg")
    }

    def getAllMessagesDeliveredTo(nodeId: NodeId): Iterable[Message] = {
      val inboxPart: Iterable[Message] = inboxes(nodeId) match {
        case Some(buf) => buf
        case None => Iterable.empty[Message]
      }

      return inboxPart ++ broadcastBuffer
    }

    def run(): Unit = {
      val electedCollectionOfNodes: Iterable[NodeId] = (0 until config.numberOfNodes).filter(nodeId => roleDistributionOracle.isNodeActive(nodeId))
      output(s"########## iteration=$iteration round=$round terminated-nodes=$terminatedNodesCounter elected-nodes=$electedCollectionOfNodes terminated-list=[${nodesWhichTerminated.mkString(",")}]")

      if (round == Round.Preround || {iteration >= 1 && round == Round.Status})
        for (i <- 0 until config.numberOfNodes) {
          val box = nodeBoxes(i)
          if (!box.context.reachedTerminationOfProtocol)
            box.node.onIterationBegin(iteration)
        }

      for (i <- SimEngineImpl.this.nodesWhichTerminated)
        output(s"//node $i has terminated with result: [${nodeBoxes(i).context.consensusResult.get.mkString(",")}]")

      //run sending phase of this round
      for (i <- 0 until config.numberOfNodes if roleDistributionOracle.isNodeActive(i)) {
        val box = nodeBoxes(i)
        if (! box.context.reachedTerminationOfProtocol)
          box.node.executeSendingPhase()
      }

      output("---phase-change---")

      //run receiving phase of this round
      for (i <- 0 until config.numberOfNodes) {
        val box = nodeBoxes(i)
        if (! box.context.reachedTerminationOfProtocol) {
          val consensusResult = box.node.executeCalculationPhase()
          if (consensusResult.isDefined)
            box.context.signalProtocolTermination(consensusResult.get)
        }
      }
    }
  }

  case class NodeBox(node: Node, context: NodeContextImpl)

  protected def output(txt: String): Unit = {
    if (out.isDefined)
      out.get.print(txt)
  }

  /*                                                 INITIALIZATION OF NODES                                          */

  def initializeNodes(): Array[NodeBox] = {
    val result = new Array[NodeBox](config.numberOfNodes)

    val numberOfHonestNodes: Int = config.numberOfNodes - config.actualNumberOfFaultyNodes
    val numberOfDeafNodes: Int = math.floor(config.actualNumberOfFaultyNodes * config.fractionOfDeafNodesAmongFaulty).toInt
    val numberOfMaliciousNodes: Int = config.actualNumberOfFaultyNodes - numberOfDeafNodes
    val inputSetsConfiguration = inputSetsGenerator.generate()

    var currentNodeId: Int = -1

    output("------------------------ node stats ---------------------------")
    output(s"nodes:")
    output(s"  total: ${config.numberOfNodes}")
    output(s"  honest: $numberOfHonestNodes")
    output(s"  faulty (total): ${config.actualNumberOfFaultyNodes}")
    output(s"      malicious: $numberOfMaliciousNodes")
    output(s"      deaf: $numberOfDeafNodes")
    output(s"average election size:")
    output(s"  normal rounds: ${config.averageNumberOfActiveNodes}")
    output(s"  leader rounds: ${config.averageNumberOfLeaders}")
    output(s"f+1=${config.faultyNodesTolerance + 1}")

    for (i <- 1 to numberOfHonestNodes) {
      currentNodeId += 1
      val context = new NodeContextImpl(currentNodeId)
      val node = config.honestNodeAlgorithm match {
        case "original" =>
          new HonestNodeFollowingGoSpacemesh(currentNodeId, config, context, inputSetsConfiguration.inputSetFor(currentNodeId), out)
        case "improved" =>
          new HonestNodeImproved(currentNodeId, config, context, inputSetsConfiguration.inputSetFor(currentNodeId), out)
        case other =>
          throw new RuntimeException(s"unsupported hones node algorithm mnemonic found in config: $other")
      }

      val box = NodeBox(node, context)
      result(currentNodeId) = box
    }

    for (i <- 1 to numberOfMaliciousNodes) {
      currentNodeId += 1
      val context = new NodeContextImpl(currentNodeId)
      val node = new GangNode(currentNodeId, config, context, inputSetsConfiguration.inputSetFor(currentNodeId), out)
      val box = NodeBox(node, context)
      result(currentNodeId) = box
    }

    for (i <- 1 to numberOfDeafNodes) {
      currentNodeId += 1
      val context = new NodeContextImpl(currentNodeId)
      val node = new DeafNode(currentNodeId, config, context, inputSetsConfiguration.inputSetFor(currentNodeId), out)
      val box = NodeBox(node, context)
      result(currentNodeId) = box
    }

    output("------------------------ input sets ---------------------------")
    for (i <- 0 until config.numberOfNodes)
      output(f"$i%03d:${result(i).node.inputSet.elements.toSeq.sorted}")

    output("-------------------- input sets overlap ---------------------------")
    var overlap = result(0).node.inputSet.elements
    for (i <- 1 until config.numberOfNodes)
      overlap = overlap.intersect(result(i).node.inputSet.elements)
    output(s"strict-overlap-of-input-sets: ${overlap.toSeq.sorted}")

    val counters = new IndexedBatteryOfIntCounters(allowNegativeValues = false)
    for (i <- 0 until config.numberOfNodes) {
      for (marble <- result(i).node.inputSet.elements) {
        counters.increment(marble, 1)
      }
    }
    val marblesWithEnoughSupport = counters.indexesWithBalanceAtLeast(config.faultyNodesTolerance + 1).toSet
    output(s"(f+1)-overlap-of-input-sets: ${marblesWithEnoughSupport.toSeq.sorted}")

    return result
  }

}
