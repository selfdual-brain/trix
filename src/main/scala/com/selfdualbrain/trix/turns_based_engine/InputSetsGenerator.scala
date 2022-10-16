package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, InputSetsConfiguration, Marble}
import org.apache.commons.math3.random.MersenneTwister

class InputSetsGenerator(config: Config) {
  val inputSetsRng = new MersenneTwister(config.inputSetsGeneratorSeed)

  def generate(): InputSetsConfiguration = {
    val result = new InputSetsConfiguration

    for (i <- 0 until config.numberOfNodes) {
      val (rangeFrom, rangeTo) = config.inputSetSizeRange
      require(rangeFrom <= rangeTo)
      val inputSetSize: Int =
        if (rangeFrom == rangeTo)
          rangeFrom
        else
          rangeFrom + inputSetsRng.nextInt(rangeTo - rangeFrom + 1)
      val marblesBuf = new Array[Marble](inputSetSize)
      for (j <- 0 until inputSetSize) {
        val marble: Marble = inputSetsRng.nextInt(config.marblesRangeForHonestNodes + 1)
        marblesBuf(j) = marble
      }
      result.registerCollection(i, new CollectionOfMarbles(marblesBuf.toSet))
    }

    return result
  }

}
