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
  def nextBytes(n: Int): Array[Byte]
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
          override def nextBytes(n: Int): Array[Byte] = internal.nextBytes(n)
        }

      case "jdk-secure-random" =>
        new RandomNumberGenerator {
          private val internal = SecureRandom.getInstance("SHA1PRNG", "SUN")
          internal.setSeed(seed)
          override def nextInt(range: Int): Int = internal.nextInt(range)
          override def nextLong(): Long = internal.nextLong()
          override def nextBoolean(): Boolean = internal.nextBoolean()
          override def nextDouble(): Double = internal.nextDouble()
          override def nextBytes(n: Int): Array[Byte] = {
            val bytes = new Array[Byte](0 max n)
            internal.nextBytes(bytes)
            return bytes
          }
        }

      case "mersenne-twister" =>
        new RandomNumberGenerator {
          private val internal = new MersenneTwister(seed)
          override def nextInt(range: Int): Int = internal.nextInt(range)
          override def nextLong(): Long = internal.nextLong()
          override def nextBoolean(): Boolean = internal.nextBoolean()
          override def nextDouble(): Double = internal.nextDouble()
          override def nextBytes(n: Int): Array[Byte] = {
            val bytes = new Array[Byte](0 max n)
            internal.nextBytes(bytes)
            return bytes
          }
        }
    }

}
