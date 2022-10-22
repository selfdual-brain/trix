package com.selfdualbrain.trix.turns_based_engine

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

//pick a random subset of fixed size
object IntIntervalSubsetPicker {

  //will pick sizeOfResult elements from [0,range] interval
  def run(rng: RandomNumberGenerator, range: Int, sizeOfResult: Int): Set[Int] = {
    require(sizeOfResult >= 0)
    require(sizeOfResult <= range + 1)

    //buffer of marbles left for selection
    val availableElements = new ArrayBuffer[Int](range + 1)
    availableElements.addAll(0 to range)

    //set of marbles we picked
    val buf = new mutable.HashSet[Int](range + 1, 0.75)

    //selection loop
    while (buf.size < sizeOfResult) {
      val selectedPosition: Int = rng.nextInt(availableElements.size)
      val marbleAtThisPosition: Int = availableElements(selectedPosition)
      availableElements.remove(selectedPosition)
      buf += marbleAtThisPosition
    }

    return buf.toSet
  }

}
