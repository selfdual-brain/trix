package com.selfdualbrain.trix.experiments

import com.selfdualbrain.continuum.textout.TextOutput
import com.selfdualbrain.trix.turns_based_engine.SimEngineImpl

object DemoRunner {

  def main(args: Array[String]): Unit = {
    val cfg = DefaultConfig
    val output = TextOutput.overConsole(4, ' ')
    val engine = new SimEngineImpl(cfg, output)

    while (engine.numberOfNodesWhichTerminated() < cfg.numberOfNodes && engine.currentIteration < 20)
      engine.playNextRound()
  }

}
