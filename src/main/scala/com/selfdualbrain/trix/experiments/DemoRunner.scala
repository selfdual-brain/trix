package com.selfdualbrain.trix.experiments

import com.selfdualbrain.continuum.textout.{AbstractTextOutput, TextOutput}
import com.selfdualbrain.trix.experiments.cfg.{MediumBlockchainRealistic, SandboxCfg, SmallBlockchainWithPerfectNetworkCfg, SmallBlockchainWithPoorNetworkCfg}
import com.selfdualbrain.trix.turns_based_engine.{Config, InputSetsGenerator, RandomNumberGenerator, RngFactory, SimEngineImpl}

object DemoRunner {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println("config must be selected via command-line parameter")
      System.exit(1)
    }

    val cfg: Config = args(0).toInt match {
      case 0 => SandboxCfg
      case 1 => MediumBlockchainRealistic
      case 2 => SmallBlockchainWithPerfectNetworkCfg
      case 3 => SmallBlockchainWithPoorNetworkCfg
    }

    val output: Option[AbstractTextOutput] = Some(TextOutput.overConsole(4, ' '))
//    val output: Option[AbstractTextOutput] = None
    val eligibilityRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.eligibilityRngSeed)
    val msgDeliveryRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.msgDeliveryRngSeed)
    val nodeDecisionsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.nodeDecisionsRngSeed)
    val inputSetsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.inputSetsGeneratorSeed)
    val inputSetsGenerator: InputSetsGenerator = new InputSetsGenerator(cfg, inputSetsRng)
    val engine = new SimEngineImpl(cfg, eligibilityRng, msgDeliveryRng, nodeDecisionsRng, inputSetsGenerator, output)

    while (engine.numberOfNodesWhichTerminated < cfg.numberOfNodes && engine.currentIteration < 50)
      engine.playNextRound()

    println("---------------------------- final stats ----------------------------")
    println(s"number of nodes which terminated: ${engine.numberOfNodesWhichTerminated}")
    println(f"           P2P messages lost [%%]: ${engine.measuredLostMessagesFraction * 100}%2.2f")
    println()
    println(s"A = cert overrides (up)")
    println(s"B = cert overrides (down)")
    println(s"C = cert overrides (non-monotonic)")
    println(s"D = equivocators discovered")
    println(s"E = empty proposal rounds")
    println()
    println("        \tA\tB\tC\tD\tE")
    println("----------------------------------")

    for (i <- 0 until cfg.numberOfNodes) {
      val hasTerminated: Boolean = engine.reachedTerminationOfProtocol(i)
      val stats = engine.nodeStats(i)
      val terminationMarker: String = if (hasTerminated) "[x]" else "[ ]"
      val consensusResult = engine.consensusResult(i)
      val a: Int = stats.notifyCertificateOverridesWithSetGoingUp
      val b: Int = stats.notifyCertificateOverridesWithSetGoingDown
      val c: Int = stats.notifyCertificateOverridesWithNonMonotonicChange
      val d: Int = stats.equivocatorsDiscovered
      val e: Int = stats.emptyProposalRounds
      println(f"$i%03d:$terminationMarker \t$a \t$b \t$c \t$d \t$e \t$consensusResult")
    }
  }

}
