package com.selfdualbrain.trix.calculations

object NetworkBandwidth {

  /*      PARAMS       */

  val numberOfNodes: Int = 100000
  val averageNumberOfMarblesInACollection: Int = 50
  val c: Int = 800 //committee size
  val d: Int = 10 //target number of leader candidates to be elected in the proposal round
  val nodeId: Int = 32
  val marbleId: Int = 20
  val msgSignature: Int = 64
  val eligibilityProof: Int = 80 //maybe we need to add here also the size of VRF "message"
  val iterationNumber: Int = 1
  val messageHash: Int = 32 //sha-265


  /*      MESSAGES       */

  val senderId: Int = nodeId
  val marblesCollectionTotal: Int = averageNumberOfMarblesInACollection * marbleId
  val msgMetadata: Int = senderId + iterationNumber + eligibilityProof + msgSignature

  class MsgVolumesProfile {
    var preRoundMsg: Int = 0
    var statusMsg: Int = 0
    var svp: Int = 0
    var proposalMsg: Int = 0
    var commitMsg: Int = 0
    var commitCertificate: Int = 0
    var notifyMsg: Int = 0
  }

  def originalProtocol: MsgVolumesProfile = {
    val p = new MsgVolumesProfile

    p.preRoundMsg = senderId + marblesCollectionTotal + eligibilityProof + msgSignature
    p.statusMsg = msgMetadata + iterationNumber + marblesCollectionTotal
    p.svp = iterationNumber + c * p.statusMsg / 2
    p.proposalMsg = msgMetadata + p.svp
    p.commitMsg = msgMetadata + marblesCollectionTotal
    p.commitCertificate = marblesCollectionTotal + iterationNumber + c * p.commitMsg / 2
    p.notifyMsg = msgMetadata + p.commitCertificate

    return p
  }

  def dmitryProtocol: MsgVolumesProfile = {
    val p = new MsgVolumesProfile

    p.preRoundMsg = senderId + marblesCollectionTotal + eligibilityProof + msgSignature + 32
    p.statusMsg = msgMetadata + iterationNumber + marblesCollectionTotal + 32
    p.svp = iterationNumber + c * p.statusMsg / 2
    p.proposalMsg = msgMetadata + p.svp + 32
    p.commitMsg = msgMetadata + marblesCollectionTotal + 32
    p.commitCertificate = marblesCollectionTotal + iterationNumber + c * p.commitMsg / 2
    p.notifyMsg = msgMetadata + p.commitCertificate + 32

    return p
  }

  def optimizedProtocol: MsgVolumesProfile = {
    val p = new MsgVolumesProfile

    p.preRoundMsg = senderId + marblesCollectionTotal + eligibilityProof + msgSignature
    p.statusMsg = msgMetadata + iterationNumber + marblesCollectionTotal
    p.svp = iterationNumber + c * messageHash + marblesCollectionTotal
    p.proposalMsg = msgMetadata + p.svp
    p.commitMsg = msgMetadata + marblesCollectionTotal
    p.commitCertificate = marblesCollectionTotal + iterationNumber + c * messageHash
    p.notifyMsg = msgMetadata + p.commitCertificate

    return p
  }

  def printResults(profile: MsgVolumesProfile): Unit = {
    val megabyte: Int = 1024 * 1024
    val preRoundTotal: Int = profile.preRoundMsg * c
    val statusTotal: Int = profile.statusMsg * c
    val proposalTotal: Int = profile.proposalMsg * d
    val commitTotal: Int = profile.commitMsg * c
    val notifyTotal: Int = profile.notifyMsg * c

    val downloadInIterationZero: Int = preRoundTotal + statusTotal + proposalTotal + commitTotal + notifyTotal
    val downloadInIterationOne: Int = statusTotal + proposalTotal + commitTotal + notifyTotal
    val download0AsMegabytes: Double = downloadInIterationZero.toDouble / megabyte
    val download1AsMegabytes: Double = downloadInIterationOne.toDouble / megabyte

    println(f"download in iteration 0: $download0AsMegabytes%4.2f")
    println(f"download in iteration 1: $download1AsMegabytes%4.2f")
    println()
    println(f"     commit certificate: ${profile.commitCertificate.toDouble / megabyte}")
    println(f"                    svp: ${profile.svp.toDouble / megabyte}")
    println()
    println(f"              pre round: ${preRoundTotal.toDouble / megabyte}%4.2f")
    println(f"           status round: ${statusTotal.toDouble / megabyte}%4.2f")
    println(f"         proposal round: ${proposalTotal.toDouble / megabyte}%4.2f")
    println(f"           commit round: ${commitTotal.toDouble / megabyte}%4.2f")
    println(f"           notify round: ${notifyTotal.toDouble / megabyte}%4.2f")
  }

  def main(args: Array[String]): Unit = {
    import java.util.Locale
    Locale.setDefault(new Locale("en", "US"))

    println("----------------- original protocol [all values in megabytes] -------------------")
    printResults(originalProtocol)
    println()

    println("----------------- Dmitry variant [all values in megabytes] -------------------")
    printResults(dmitryProtocol)
    println()

    println("----------------- optimized protocol [all values in megabytes] -------------------")
    printResults(optimizedProtocol)
  }

}
