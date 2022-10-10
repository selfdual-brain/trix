package com.selfdualbrain.trix.event_based_engine
import com.selfdualbrain.continuum.time.{SimTimepoint, TimeDelta}
import com.selfdualbrain.trix.protocol_model.Message

import scala.util.Random

class ValidatorContextImpl extends ValidatorContext {

  override def random: Random = ???

  override def broadcast(timepointOfPassingTheBrickToCommsLayer: SimTimepoint, message: Message, cpuTimeConsumed: TimeDelta): Unit = ???

  override def scheduleNextRoundWakeUp(wakeUpTimepoint: SimTimepoint): Unit = ???

  override def addOutputEvent(payload: TrixEventPayload): Unit = ???

  override def time(): SimTimepoint = ???

  override def registerProcessingGas(gas: Long): Unit = ???
}
