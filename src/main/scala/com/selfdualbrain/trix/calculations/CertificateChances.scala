package com.selfdualbrain.trix.calculations

import breeze.stats.distributions.Gaussian
import breeze.stats.distributions._
import Rand.FixedSeed._

import java.math.MathContext

object CertificateChances {

  val mc = new MathContext(200)

  def main(args: Array[String]): Unit = {

    val numberOfNodes: Int = 100000
    val committeeSize: Int = 800
    val terminationPercentageRange = 50 to 65
    val exactCalculationEnabled: Boolean = false

    println(s"number of nodes: $numberOfNodes")
    println(s"committee size: $committeeSize")

    for (i <- terminationPercentageRange) {
      val exactResult: Double =
        if (exactCalculationEnabled)
          hareCertificateChancesExact(numberOfNodes, committeeSize, i.toDouble/100) * 100
        else
          0.0
      val gaussResult: Double = hareCertificateChancesGauss(numberOfNodes, committeeSize, i.toDouble/100) * 100

      println(f"$i%%: $exactResult%2.15f $gaussResult%2.15f")
    }

  }

  def newton(n: Int, k: Int): BigInt = factorial(n)/(factorial(k)*(factorial(n-k)))

  def factorial(n: Int): BigInt = {
    if (n == 0 || n == 1)
      return BigInt(1)

    var accumulator: BigInt = BigInt(1)
    for (i <- 1 to n)
      accumulator = accumulator * i

    return accumulator
  }

  def psi(w: Int, b: Int, x: Int, k: Int): BigDecimal = {
//    println(s"newton(w,x)=${newton(w,x)}")
//    println(s"newton(b, k-x)=${newton(b, k-x)}")
//    println(s"newton(w+b,k)=${newton(w+b,k)}")

    val exactAnswer = BigDecimal(newton(w,x), mc) * BigDecimal(newton(b, k-x), mc) / BigDecimal(newton(w+b,k), mc)
    return exactAnswer
  }

  //calculates chances that k-sample will contain at most m white balls
  def f(w: Int, b: Int, k: Int, m: Int): Double = {
    require (m <= k)
    //(0 to m).map(x => psi(w, b, x, k)).sum

    var accumulator: BigDecimal = BigDecimal(0, mc)
    for (x <- 0 to m) {
      val r: BigDecimal = psi(w, b, x, k)
//      println(s"->psi(w=$w,b=$b,x=$x,k=$k)=$r")
      accumulator += r
    }
//    println(s"**--->accumulator=$accumulator")
    return accumulator.toDouble
  }

  def hareCertificateChancesExact(n: Int, k: Int, p: Double): Double = {
    val m: Int =
      if (k % 2 == 0)
        k / 2 - 1
      else
        k / 2
    f(n - (n*p).toInt, (n*p).toInt, k, m)
  }

  def hareCertificateChancesGauss(n: Int, k: Int, p: Double): Double = {
    val distribution = new Gaussian(p, math.sqrt(p*(1-p)/k))
    return 1 - distribution.cdf(0.5)
  }

}
