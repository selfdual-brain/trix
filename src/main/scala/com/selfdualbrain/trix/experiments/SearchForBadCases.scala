package com.selfdualbrain.trix.experiments

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}
import com.selfdualbrain.trix.turns_based_engine.{Config, InputSetsGenerator, RandomNumberGenerator, RngFactory, SimEngine, SimEngineImpl}

object SearchForBadCases {
  val HARE_ITERATIONS: Int = 20
  val TEST_CASES_TO_CHECK: Int = 1000

  val cfgTemplate = new TestCfg(0)

  def main(args: Array[String]): Unit = {

    var smallestNumberOfTerminatorsWinner: Int = -1
    var smallestNumberOfTerminators: Int = Int.MaxValue
    var smallestNumberOfTerminatorsLMFraction: Double = 0

    var biggestNumberOfRoundsWithTerminationWinner: Int = -1
    var biggestNumberOfRoundsWithTerminatingNodes: Int = 0
    var biggestNumberOfRoundsLMFraction: Double = 0

    var totalNumberOfTerminators: Int = 0
    var lessThan75PercentTerminatorsCaseCounter: Int = 0
    var lessThan60PercentTerminatorsCaseCounter: Int = 0

    var emptyConsensusResultCounter: Int = 0
    var consistencyViolationsCounter: Int = 0
    var emptySetWasConsensusResultCounter: Int = 0

    val badCasesThreshold75: Int = (cfgTemplate.numberOfNodes * 0.75).toInt
    val badCasesThreshold60: Int = (cfgTemplate.numberOfNodes * 0.60).toInt

    for (i <- 0 until TEST_CASES_TO_CHECK ) {
      if (i % 10 == 0)
        println(s"running simulation $i")
      val cfg = new TestCfg(i)
      val eligibilityRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.eligibilityRngSeed)
      val msgDeliveryRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.msgDeliveryRngSeed)
      val nodeDecisionsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.nodeDecisionsRngSeed)
      val inputSetsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.inputSetsGeneratorSeed)
      val inputSetsGenerator: InputSetsGenerator = new InputSetsGenerator(cfg, inputSetsRng)
      val engine = new SimEngineImpl(cfg, eligibilityRng, msgDeliveryRng, nodeDecisionsRng, inputSetsGenerator, out = None)

      //play the whole simulation
      while (engine.numberOfNodesWhichTerminated < cfg.numberOfNodes && engine.currentIteration < HARE_ITERATIONS)
        engine.playNextRound()

      //update stats
      if (engine.numberOfNodesWhichTerminated < smallestNumberOfTerminators) {
        smallestNumberOfTerminatorsWinner = i
        smallestNumberOfTerminators = engine.numberOfNodesWhichTerminated
        smallestNumberOfTerminatorsLMFraction = engine.measuredLostMessagesFraction
      }

      if (engine.numberOfRoundsWithTermination > biggestNumberOfRoundsWithTerminatingNodes) {
        biggestNumberOfRoundsWithTerminationWinner = i
        biggestNumberOfRoundsWithTerminatingNodes = engine.numberOfRoundsWithTermination
        biggestNumberOfRoundsLMFraction = engine.measuredLostMessagesFraction
      }

      totalNumberOfTerminators += engine.numberOfNodesWhichTerminated
      if (engine.numberOfNodesWhichTerminated < badCasesThreshold75)
        lessThan75PercentTerminatorsCaseCounter += 1
      if (engine.numberOfNodesWhichTerminated < badCasesThreshold60)
        lessThan60PercentTerminatorsCaseCounter += 1

      val (commonConsensusResult, consistencyCheckOK) = consistencyCheck(engine)
      if (! consistencyCheckOK)
        consistencyViolationsCounter += 1

      if (consistencyCheckOK && commonConsensusResult.isDefined && commonConsensusResult.get.isEmpty)
        emptySetWasConsensusResultCounter += 1
    }

    println("------------------------------ results ------------------------------")
    println(s"test cases checked: $TEST_CASES_TO_CHECK")
    println(s"hare iterations: $HARE_ITERATIONS")
    println(f"average fraction of terminators [%%]: ${totalNumberOfTerminators.toDouble / cfgTemplate.numberOfNodes / TEST_CASES_TO_CHECK * 100}%2.4f")
    println(f"fraction of cases when less than 75%% nodes were able to terminate [%%]: ${lessThan75PercentTerminatorsCaseCounter.toDouble / TEST_CASES_TO_CHECK * 100}%2.4f")
    println(f"fraction of cases when less than 60%% nodes were able to terminate [%%]: ${lessThan60PercentTerminatorsCaseCounter.toDouble / TEST_CASES_TO_CHECK * 100}%2.4f")

    println(s"average iteration when a node hits 'ready to terminate' status: TODO")
    println(s"number of consistency violations: $consistencyViolationsCounter")
    println(f"number of cases when consensus result was empty: $emptySetWasConsensusResultCounter")
    println()
    println(s"worst case in category 'smallest number of terminators'")
    println(s"    number of terminators: $smallestNumberOfTerminators")
    println(s"    random seed: $smallestNumberOfTerminatorsWinner")
    println(f"    messages lost [%%]: ${smallestNumberOfTerminatorsLMFraction * 100}%2.2f")
    println()
    println(s"worst case in category 'termination spread across many iterations'")
    println(s"    number of rounds with termination: $biggestNumberOfRoundsWithTerminatingNodes")
    println(s"    random seed: $biggestNumberOfRoundsWithTerminationWinner")
    println(f"    messages lost [%%]: ${biggestNumberOfRoundsLMFraction * 100}%2.2f")
  }

  private def consistencyCheck(engine: SimEngine): (Option[CollectionOfMarbles], Boolean) = {
    var commonConsensusResult: Option[CollectionOfMarbles] = None
    for (i <- 0 until cfgTemplate.numberOfNodes) {
      engine.consensusResult(i) match {
        case None =>
          //do nothing
        case Some(coll) =>
          if (! commonConsensusResult.isDefined)
            commonConsensusResult = Some(coll)
          else if (commonConsensusResult.get != coll)
            return (commonConsensusResult, false)

      }
    }
    return (commonConsensusResult, true)
  }

//  class TestCfg(seed: Long) extends Config {
//    override val maxFractionOfFaultyNodes: Double = 0
//    override val fractionOfDeafNodesAmongFaulty: Double = 0
//
//    override val inputSetSizeRange: (Int, Int) = (15, 20)
//    override val marblesRangeForHonestNodes: Int = 20
//
//    override val isNetworkReliable: Boolean = false
//    override val probabilityOfAMessageGettingLost: Double = 0.1
//
//    override val numberOfNodes: Int = 10
//    override val averageNumberOfActiveNodes: Double = 5
//    override val averageNumberOfLeaders: Double = 1
//
//    override val eligibilityRngSeed: Long = seed
//    override val nodeDecisionsRngSeed: Long = seed + 1
//    override val msgDeliveryRngSeed: Long = seed + 2
//    override val inputSetsGeneratorSeed: Long = seed + 3
//    override val rngAlgorithm: String = "jdk-std"
//
//    override val initialSizeOfInboxBuffer: Int = 10
//    override val manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]] = None
//
//    override val enforceFixedNumberOfActiveNodes: Boolean = true
//    override val ignoreSecondNotifyFromTheSameSender: Boolean = true
//    override val resetNotificationsCounterAtEveryIteration: Boolean = true
//
//    override val zombieIterationsLimit: NodeId = 0
//  }

  class TestCfg(seed: Long) extends Config {
    override val maxFractionOfFaultyNodes: Double = 0
    override val fractionOfDeafNodesAmongFaulty: Double = 0

    override val inputSetSizeRange: (Int, Int) = (15, 20)
    override val marblesRangeForHonestNodes: Int = 20

    override val isNetworkReliable: Boolean = false
    override val probabilityOfAMessageGettingLost: Double = 0.2

    override val numberOfNodes: Int = 100
    override val averageNumberOfActiveNodes: Double = 10
    override val averageNumberOfLeaders: Double = 1

    override val eligibilityRngSeed: Long = seed
    override val nodeDecisionsRngSeed: Long = seed + 1
    override val msgDeliveryRngSeed: Long = seed + 2
    override val inputSetsGeneratorSeed: Long = seed + 3
    override val rngAlgorithm: String = "jdk-std"

    override val initialSizeOfInboxBuffer: Int = 10
    override val manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]] = None

    override val enforceFixedNumberOfActiveNodes: Boolean = true
    override val ignoreSecondNotifyFromTheSameSender: Boolean = true
    override val resetNotificationsCounterAtEveryIteration: Boolean = true

    override val zombieIterationsLimit: NodeId = 0
  }

}
