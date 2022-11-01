package com.selfdualbrain.trix.turns_based_engine

trait NodeStats {
  def notifyCertificateOverridesWithSetGoingUp: Int
  def notifyCertificateOverridesWithSetGoingDown: Int
  def notifyCertificateOverridesWithNonMonotonicChange: Int
  def equivocatorsDiscovered: Int
  def emptyProposalRounds: Int
}

object NodeStats {

  object EmptyMock extends NodeStats {
    override def notifyCertificateOverridesWithSetGoingUp: Int = 0
    override def notifyCertificateOverridesWithSetGoingDown: Int = 0
    override def notifyCertificateOverridesWithNonMonotonicChange: Int = 0
    override def equivocatorsDiscovered: Int = 0
    override def emptyProposalRounds: Int = 0
  }

}
