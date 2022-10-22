package com.selfdualbrain.trix.experiments

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}
import com.selfdualbrain.trix.turns_based_engine.{Config, InputSetsGenerator, RandomNumberGenerator, RngFactory, SimEngineImpl}

object SearchForBadCases {
  val HARE_ITERATIONS: Int = 20
  val TEST_CASES_TO_CHECK: Int = 1000

  def main(args: Array[String]): Unit = {

    var numberOfWinner: Int = -1
    var smallestNumberOfTerminators: Int = Int.MaxValue



    for (i <- 0 until TEST_CASES_TO_CHECK ) {
      println(s"running simulation $i")
      val cfg = new TestCfg(i)
      val eligibilityRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.eligibilityRngSeed)
      val msgDeliveryRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.msgDeliveryRngSeed)
      val nodeDecisionsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.nodeDecisionsRngSeed)
      val inputSetsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.inputSetsGeneratorSeed)
      val inputSetsGenerator: InputSetsGenerator = new InputSetsGenerator(cfg, inputSetsRng)
      val engine = new SimEngineImpl(cfg, eligibilityRng, msgDeliveryRng, nodeDecisionsRng, inputSetsGenerator, out = None)
      while (engine.numberOfNodesWhichTerminated() < cfg.numberOfNodes && engine.currentIteration < HARE_ITERATIONS)
        engine.playNextRound()
      if (engine.numberOfNodesWhichTerminated() < smallestNumberOfTerminators) {
        numberOfWinner = i
        smallestNumberOfTerminators = engine.numberOfNodesWhichTerminated()
      }
    }

    println("------------------------------ results ------------------------------")
    println(s"hare iterations: $HARE_ITERATIONS")
    println(s"worst case found: only $smallestNumberOfTerminators nodes reached termination of the protocol")
    println(s"random seed: $numberOfWinner")
  }

  class TestCfg(seed: Long) extends Config {
    override val maxFractionOfFaultyNodes: Double = 0
    override val fractionOfDeafNodesAmongFaulty: Double = 0

    override val inputSetSizeRange: (Int, Int) = (15, 20)
    override val marblesRangeForHonestNodes: Int = 20

    override val isNetworkReliable: Boolean = false
    override val probabilityOfAMessageGettingLost: Double = 0.3

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
  }


}
