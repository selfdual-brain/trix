package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, InputSetsConfiguration}

/**
 * Generates input sets for all nodes participating in the simulation.
 */
class InputSetsGenerator(config: Config, rng: RandomNumberGenerator) {
//  val inputSetsRng = new MersenneTwister(config.inputSetsGeneratorSeed)

  def generate(): InputSetsConfiguration = {
    //this data structure will accumulate marble configurations for all nodes
    val result = new InputSetsConfiguration

    //checking if the input size range provided in the config is formally valid
    val (rangeFrom, rangeTo) = config.inputSetSizeRange
    require(rangeFrom <= rangeTo)

    //looping over all node ids
    for (i <- 0 until config.numberOfNodes) {

      //picking input set size we want to generate for current node
      val desiredInputSetSize: Int =
        if (rangeFrom == rangeTo)
          rangeFrom
        else
          rangeFrom + rng.nextInt(rangeTo - rangeFrom + 1)

      val selectedSubset = IntIntervalSubsetPicker.run(rng, config.marblesRangeForHonestNodes, desiredInputSetSize)
      result.registerCollection(i, new CollectionOfMarbles(selectedSubset))
    }

    return result
  }

}
