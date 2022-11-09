package com.selfdualbrain.trix.protocol_model

/**
 * Represents an immutable collection of marbles.
 * The whole hare protocol is about deciding what will be the final collection of marbles.
 *
 * Remark: Encapsulated as a class so we can later easily change the implementation.
 */
class CollectionOfMarbles(val elements: Set[Marble]) extends Iterable[Marble] {
  private val elementsAfterSorting: Seq[Marble] = elements.toSeq.sorted

  override def equals(obj: Any): Boolean = {
    if (obj == null)
      return false

    obj match {
      case x: CollectionOfMarbles => x.elements == elements
      case other => false
    }
  }

  override def hashCode(): Marble = elementsAfterSorting.hashCode()

  override def toString: String = elementsAfterSorting.toString

  override def iterator: Iterator[Marble] = elementsAfterSorting.iterator

  override def isEmpty: Boolean = elements.isEmpty


}
object CollectionOfMarbles {
  val empty = new CollectionOfMarbles(Set.empty[Marble])
}
