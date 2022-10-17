package com.selfdualbrain.trix.protocol_model

/**
 * Represents an immutable collection of marbles.
 * The whole hare protocol is about deciding what will be the final collection of marbles.
 *
 * Remark: Encapsulated as a class so we can later easily change the implementation.
 */
class CollectionOfMarbles(val elements: Set[Marble]) {
}
object CollectionOfMarbles {
  val empty = new CollectionOfMarbles(Set.empty[Marble])
}
