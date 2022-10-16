package com.selfdualbrain.trix.protocol_model

import scala.collection.mutable

class InputSetsConfiguration {
  private val internalMap: mutable.Map[NodeId, CollectionOfMarbles] = new mutable.HashMap[NodeId, CollectionOfMarbles]

  def registerCollection(nodeId: NodeId, inputSet: CollectionOfMarbles): Unit = {
    internalMap += nodeId -> inputSet
  }

  def inputSetFor(nodeId: NodeId): CollectionOfMarbles = internalMap(nodeId)

}
