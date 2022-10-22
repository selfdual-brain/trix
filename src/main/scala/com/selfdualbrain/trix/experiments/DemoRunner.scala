package com.selfdualbrain.trix.experiments

import com.selfdualbrain.continuum.textout.TextOutput
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

    val output = Some(TextOutput.overConsole(4, ' '))
    val eligibilityRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.eligibilityRngSeed)
    val msgDeliveryRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.msgDeliveryRngSeed)
    val nodeDecisionsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.nodeDecisionsRngSeed)
    val inputSetsRng: RandomNumberGenerator = RngFactory.getInstance(cfg.rngAlgorithm, cfg.inputSetsGeneratorSeed)
    val inputSetsGenerator: InputSetsGenerator = new InputSetsGenerator(cfg, inputSetsRng)
    val engine = new SimEngineImpl(cfg, eligibilityRng, msgDeliveryRng, nodeDecisionsRng, inputSetsGenerator, output)

    while (engine.numberOfNodesWhichTerminated() < cfg.numberOfNodes && engine.currentIteration < 20)
      engine.playNextRound()
  }

}
