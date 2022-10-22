package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.textout.AbstractTextOutput
import com.selfdualbrain.trix.protocol_model.{Message, NodeId, Round}
import org.apache.commons.math3.random.MersenneTwister

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

class SimEngineImpl(
                     config: Config,
                     eligibilityRng: RandomNumberGenerator,
                     msgDeliveryRng: RandomNumberGenerator,
                     nodeDecisionsRng: RandomNumberGenerator,
                     out: Option[AbstractTextOutput]
                   ) extends SimEngine {

  private val nodeBoxes: Array[NodeBox] = initializeNodes()
  var currentRound: Option[Round] = None
  var currentIteration: Int = 0
  private var currentRoundProcess: Option[SingleRoundProcess] = None
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

    val process = new SingleRoundProcess(
      iteration = currentIteration,
      round = currentRound.get,
      rng = eligibilityRng
    )
    currentRoundProcess = Some(process)
    process.run()
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

    override def broadcastIncludingMyself(msg: Message): Unit = {
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
        return messagesCollection.filter(msg => {
          val shouldBeLost: Boolean = msgDeliveryRng.nextDouble() < config.probabilityOfAMessageGettingLost
          if (shouldBeLost)
            output(s"$nodeId:incoming-message-lost:$msg")
          !shouldBeLost
        })
    }

    override def signalProtocolTermination(): Unit = {
      terminatedFlag = true
      terminatedNodesCounter += 1
    }

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
      new CachingRoleDistributionOracle(config.numberOfNodes, electedSubsetAverageSize, rng)

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
      output(s"########## iteration=$iteration round=$round terminated-nodes=$terminatedNodesCounter elected-nodes=$electedCollectionOfNodes")

      //run sending phase of this round
      for (i <- 0 until config.numberOfNodes if roleDistributionOracle.isNodeActive(i)) {
        val box = nodeBoxes(i)
        if (! box.context.reachedTerminationOfProtocol)
          box.node.executeSendingPhase()
      }

      //run receiving phase of this round
      for (i <- 0 until config.numberOfNodes) {
        val box = nodeBoxes(i)
        if (! box.context.reachedTerminationOfProtocol) {
          val termination = nodeBoxes(i).node.executeCalculationPhase()
          if (termination)
            box.context.signalProtocolTermination()
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

    val inputSetsGenerator = new InputSetsGenerator(config)
    val inputSetsConfiguration = inputSetsGenerator.generate()

    var currentNodeId: Int = -1

    println("------------------------ node stats ---------------------------")
    println(s"nodes:")
    println(s"  total: ${config.numberOfNodes}")
    println(s"  honest: $numberOfHonestNodes")
    println(s"  faulty (total): ${config.actualNumberOfFaultyNodes}")
    println(s"      malicious: $numberOfMaliciousNodes")
    println(s"      deaf: $numberOfDeafNodes")
    println(s"average election size:")
    println(s"  normal rounds: ${config.averageNumberOfActiveNodes}")
    println(s"  leader rounds: ${config.averageNumberOfLeaders}")
    println(s"f+1=${config.faultyNodesTolerance + 1}")

    for (i <- 1 to numberOfHonestNodes) {
      currentNodeId += 1
      val context = new NodeContextImpl(currentNodeId)
      val node = new HonestNode(currentNodeId, config, context, inputSetsConfiguration.inputSetFor(currentNodeId), out)
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

    println("------------------------ input sets ---------------------------")
    for (i <- 0 until config.numberOfNodes)
      println(f"$i%03d:${result(i).node.inputSet.elements.toSeq.sorted}")

    return result
  }

}
