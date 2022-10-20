package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, InputSetsConfiguration, Marble}
import org.apache.commons.math3.random.MersenneTwister

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Generates input sets for all nodes participating in the simulation.
 */
class InputSetsGenerator(config: Config) {
  val inputSetsRng = new MersenneTwister(config.inputSetsGeneratorSeed)

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
          rangeFrom + inputSetsRng.nextInt(rangeTo - rangeFrom + 1)

      //buffer of marbles left for selection
      val availableMarbles = new ArrayBuffer[Marble](config.marblesRangeForHonestNodes + 1)
      availableMarbles.addAll(0 to config.marblesRangeForHonestNodes)

      //set of marbles we picked
      val buf = new mutable.HashSet[Marble](rangeTo, 0.75)

      //selection loop
      while (buf.size < desiredInputSetSize) {
        val selectedPosition: Int = inputSetsRng.nextInt(availableMarbles.size)
        val marbleAtThisPosition: Int = availableMarbles(selectedPosition)
        availableMarbles.remove(selectedPosition)
        buf += marbleAtThisPosition
      }

      result.registerCollection(i, new CollectionOfMarbles(buf.toSet))
    }

    return result
  }

}
