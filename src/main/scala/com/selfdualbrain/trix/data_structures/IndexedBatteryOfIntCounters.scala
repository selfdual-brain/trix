package com.selfdualbrain.trix.data_structures

import scala.collection.mutable

class IndexedBatteryOfIntCounters(allowNegativeValues: Boolean) {
  private val counters = new mutable.HashMap[Int, Counter]
  private var totalX: Int = 0

  def currentValue(index: Int): Int =
    counters.get(index) match {
      case None => 0
      case Some(holder) => holder.value
    }

  def update(index: Int, delta: Int): Unit = {
    val updatedValue: Int = counters.get(index) match {
      case None =>
        val holder = new Counter
        holder.value = delta
        counters += index -> holder
        holder.value
      case Some(holder) =>
        holder.value += delta
        holder.value
    }
    if (! allowNegativeValues)
      assert (updatedValue >= 0, s"negative balance at index=$index; reached value $updatedValue")
    totalX += delta
  }

  def increment(index: Int, delta: Int): Unit = {
    assert (delta >= 0)
    this.update(index, delta)
  }

  def decrement(index: Int, delta: Int): Unit = {
    assert (delta >= 0)
    this.update(index, - delta)
  }

  def reset(index: Int): Unit = {
    counters.get(index) match {
      case None =>
      //do nothing
      case Some(holder) =>
        holder.value = 0
    }
  }

  def total: Int = totalX

  def indexesWithNonZeroBalance: Iterable[Int] = counters collect {case (i,c) if c.value != 0 => i}

  def indexesWithBalanceAtLeast(n: Int): Iterable[Int] = counters collect {case (i,c) if c.value >= n => i}

}

class Counter {
  var value: Int = 0
}
