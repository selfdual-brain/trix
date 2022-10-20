package com.selfdualbrain.trix.turns_based_engine

import com.selfdualbrain.continuum.textout.{AbstractTextOutput, TextOutput}
import com.selfdualbrain.trix.protocol_model.{CollectionOfMarbles, NodeId}

import scala.collection.mutable

abstract class Node(val id: NodeId, simConfig: Config, context: NodeContext, val inputSet: CollectionOfMarbles, out: AbstractTextOutput) {
  override def toString: String = s"node-$id"

  def executeSendingPhase(): Unit

  //returns true if protocol termination was achieved
  def executeCalculationPhase(): Boolean

  protected def output(code: String, body: String): Unit = {
    out.print(s"$id:$code:$body")
  }
}
