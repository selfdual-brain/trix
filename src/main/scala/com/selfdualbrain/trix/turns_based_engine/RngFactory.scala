package com.selfdualbrain.trix.turns_based_engine

import org.apache.commons.math3.random.MersenneTwister

import java.security.SecureRandom
import scala.util.Random

//we need a custom trait to represent random number generators so to switch between different implementations
//both java and scala classes "Random" are (unfortunately) not easy to extend
trait RandomNumberGenerator {
  def nextInt(range: Int): Int
  def nextLong(): Long
  def nextBoolean(): Boolean
  def nextDouble(): Double
}

object RngFactory {

  def getInstance(algorithmName: String, seed: Long): RandomNumberGenerator =
    algorithmName match {
      case "jdk-std" =>
        new RandomNumberGenerator {
          private val internal = new Random(seed)
          override def nextInt(range: Int): Int = internal.nextInt(range)
          override def nextLong(): Long = internal.nextLong()
          override def nextBoolean(): Boolean = internal.nextBoolean()
          override def nextDouble(): Double = internal.nextDouble()
        }

      case "jdk-secure-random" =>
        new RandomNumberGenerator {
          private val internal = SecureRandom.getInstance("SHA1PRNG", "SUN")
          internal.setSeed(seed)
          override def nextInt(range: Int): Int = internal.nextInt(range)
          override def nextLong(): Long = internal.nextLong()
          override def nextBoolean(): Boolean = internal.nextBoolean()
          override def nextDouble(): Double = internal.nextDouble()
        }

      case "mersenne-twister" =>
        new RandomNumberGenerator {
          private val internal = new MersenneTwister(seed)
          override def nextInt(range: Int): Int = internal.nextInt(range)
          override def nextLong(): Long = internal.nextLong()
          override def nextBoolean(): Boolean = internal.nextBoolean()
          override def nextDouble(): Double = internal.nextDouble()
        }
    }

}
