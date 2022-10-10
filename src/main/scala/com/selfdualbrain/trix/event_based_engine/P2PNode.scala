package com.selfdualbrain.trix.event_based_engine

/**
 * Represents P2P network identification of a computer (process).
 *
 * Caution: not to be mistaken with ValidatorId (which is consensus-protocol-level identity, based on cryptography).
 * We need this distinction (which mimics real-live, actually) so to be able to simulate certain attack scenarios.
 */
class P2PNode(address: Int) {
  override def toString: String = f"node-$address%03d"

}
