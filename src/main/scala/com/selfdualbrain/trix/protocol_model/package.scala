package com.selfdualbrain.trix

import com.selfdualbrain.trix.cryptography.Hash

package object protocol_model {
  type NodeId = Int

  //elements in consensus collection
  type Marble = Int

  type VrfProof = Long

  type Ed25519Sig = Hash

}
