package com.selfdualbrain.trix.experiments

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}
import com.selfdualbrain.trix.turns_based_engine.{Config, InputSetsGenerator, RandomNumberGenerator, RngFactory, SimEngineImpl}

object SearchForBadCases {
  val HARE_ITERATIONS: Int = 30
  val TEST_CASES_TO_CHECK: Int = 2000

  def main(args: Array[String]): Unit = {

    var smallestNumberOfTerminatorsWinner: Int = -1
    var smallestNumberOfTerminators: Int = Int.MaxValue
    var smallestNumberOfTerminatorsLMFraction: Double = 0

    var biggestNumberOfRoundsWithTerminationWinner: Int = -1
    var biggestNumberOfRoundsWithTerminatingNodes: Int = 0
    var biggestNumberOfRoundsLMFraction: Double = 0

    for (i <- 0 until TEST_CASES_TO_CHECK ) {
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

      //update records
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
    }

    println("------------------------------ results ------------------------------")
    println(s"hare iterations: $HARE_ITERATIONS")
    println(s"worst case in category 'smallest number of terminators'")
    println(s"    number of terminators: $smallestNumberOfTerminators")
    println(s"    random seed: $smallestNumberOfTerminatorsWinner")
    println(f"    messages lost [%%]: ${smallestNumberOfTerminatorsLMFraction * 100}%2.2f")
    println(s"worst case in category 'termination spread across many iterations'")
    println(s"    number of rounds with termination: $biggestNumberOfRoundsWithTerminatingNodes")
    println(s"    random seed: $biggestNumberOfRoundsWithTerminationWinner")
    println(f"    messages lost [%%]: ${biggestNumberOfRoundsLMFraction * 100}%2.2f")
  }

  class TestCfg(seed: Long) extends Config {
    override val maxFractionOfFaultyNodes: Double = 0
    override val fractionOfDeafNodesAmongFaulty: Double = 0

    override val inputSetSizeRange: (Int, Int) = (15, 20)
    override val marblesRangeForHonestNodes: Int = 20

    override val isNetworkReliable: Boolean = false
    override val probabilityOfAMessageGettingLost: Double = 0.2

    override val numberOfNodes: Int = 10
    override val averageNumberOfActiveNodes: Double = 5
    override val averageNumberOfLeaders: Double = 1

    override val eligibilityRngSeed: Long = seed
    override val nodeDecisionsRngSeed: Long = seed + 1
    override val msgDeliveryRngSeed: Long = seed + 2
    override val inputSetsGeneratorSeed: Long = seed + 3
    override val rngAlgorithm: String = "jdk-std"

    override val initialSizeOfInboxBuffer: Int = 10
    override val manuallyProvidedInputSets: Option[Map[NodeId, CollectionOfMarbles]] = None

    override val enforceFixedNumberOfActiveNodes: Boolean = true

    override def zombieIterationsLimit: NodeId = 5
  }


}
