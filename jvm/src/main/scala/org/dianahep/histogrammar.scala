package org.dianahep

import scala.language.implicitConversions

package object histogrammar {
  type Weighting[DATUM] = Weighted[DATUM] => Double

  implicit def toWeighted[DATUM](datum: DATUM) = Weighted(datum)
  implicit def domainToWeighted[DOMAIN, RANGE](f: DOMAIN => RANGE) = {x: Weighted[DOMAIN] => f(x.datum)}

  implicit def filterToWeight[WEIGHTED <: Weighted[_]](filter: WEIGHTED => Boolean) = {x: WEIGHTED => if (filter(x)) 1.0 else 0.0}

  implicit def noWeighting[DATUM] = {x: Weighted[DATUM] => 1.0}

  implicit class HistogramMethods[DATUM](hist: Binned[DATUM, Count[DATUM]]) {
    def show = {
      val minCount = hist.values.map(_.value).min
      val maxCount = hist.values.map(_.value).max
      val range = maxCount - minCount
      val minEdge = if (minCount < 0.0) minCount - 0.1*range else 0.0
      val maxEdge = maxCount + 0.1*range

      val binWidth = (hist.high - hist.low) / hist.num
      def sigfigs(x: Double, n: Int) = new java.math.BigDecimal(x).round(new java.math.MathContext(n)).toString

      val prefixValues = hist.values.zipWithIndex map {case (v, i) =>
        val binlow = sigfigs(i * binWidth + hist.low, 3)
        val binhigh = sigfigs((i + 1) * binWidth + hist.low, 3)
        val value = sigfigs(v.value, 4)
        (binlow, binhigh, value)
      }
      val widestBinlow = prefixValues.map(_._1.size).max
      val widestBinhigh = prefixValues.map(_._2.size).max
      val widestValue = prefixValues.map(_._3.size).max
      val formater = s"[%-${widestBinlow}s, %-${widestBinhigh}s) %-${widestValue}s "
      val prefixWidth = widestBinlow + widestBinhigh + widestValue + 6
      
      val width = 80 - prefixWidth
      val zeroIndex = Math.round(width * (0.0 - minEdge) / (maxEdge - minEdge)).toInt
      val zeroLine1 = " " * prefixWidth + (if (zeroIndex > 0) " " else "") + " " * zeroIndex + "0" + " " * (width - zeroIndex - 10) + " " + f"$maxEdge%10g"
      val zeroLine2 = " " * prefixWidth + (if (zeroIndex > 0) "+" else "") + "-" * zeroIndex + "+" + "-" * (width - zeroIndex - 1) + "-" + "+"

      val lines = hist.values zip prefixValues map {case (v, (binlow, binhigh, value)) =>
        val peakIndex = Math.round(width * (v.value - minEdge) / (maxEdge - minEdge)).toInt
        if (peakIndex < zeroIndex)
          formater.format(binlow, binhigh, value) + (if (zeroIndex > 0) "|" else "") + " " * peakIndex + "*" * (zeroIndex - peakIndex) + "|" + " " * (width - zeroIndex) + "|"
        else
          formater.format(binlow, binhigh, value) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (peakIndex - zeroIndex) + " " * (width - peakIndex) + "|"
      }
      (List(zeroLine1, zeroLine2) ++ lines ++ List(zeroLine2)).mkString("\n")      
    }
  }
}

package histogrammar {
  case class Weighted[DATUM](datum: DATUM, weight: Double = 1.0) {
    def *(w: Double): Weighted[DATUM] = copy(weight = weight*w)
  }

  trait Aggregator[DATUM, SELF] {
    def apply(x: Weighted[DATUM]): Unit
    def copy: SELF
    def +(that: SELF): SELF
    def weighting: Weighting[DATUM]

    def name = getClass.getSimpleName
    def constructor: String
    def state: String
    override def toString() = s"$name($constructor)($state)"
  }

  trait Container[DATUM, SELF, SUB <: Aggregator[DATUM, SUB]] extends Aggregator[DATUM, SELF]

  class Count[DATUM]
             (val weighting: Weighting[DATUM])
             (var value: Double = 0.0)
             extends Aggregator[DATUM, Count[DATUM]] {
    def apply(x: Weighted[DATUM]) {
      val y = x * weighting(x)
      value += y.weight
    }
    def copy = new Count(weighting)(value)
    def +(that: Count[DATUM]) = new Count(weighting)(this.value + that.value)

    def constructor = ""
    def state = value.toString
  }
  object Count {
    def apply[DATUM](implicit weighting: Weighting[DATUM]) = new Count(weighting)()
  }

  class Binned[DATUM, SUB <: Aggregator[DATUM, SUB]]
              (sub: SUB, val num: Int, val low: Double, val high: Double, val key: Weighted[DATUM] => Double, val weighting: Weighting[DATUM])
              (val values: Vector[SUB] = Vector.fill(num)(sub.copy), val underflow: SUB = sub.copy, val overflow: SUB = sub.copy)
              extends Container[DATUM, Binned[DATUM, SUB], SUB] {
    if (low >= high)
      throw new IllegalArgumentException(s"low ($low) must be less than high ($high)")
    if (num < 1)
      throw new IllegalArgumentException(s"num ($num) must be greater than zero")

    def bin(k: Double): Int = {
      val out = Math.floor(num * (k - low) / (high - low)).toInt
      if (out < 0  ||  out >= num)
        -1
      else
        out
    }
    def under(k: Double): Boolean = k < low
    def over(k: Double): Boolean = k >= high

    def apply(x: Weighted[DATUM]) {
      val k = key(x)
      val y = x * weighting(x)
      if (under(k))
        underflow(y)
      else if (over(k))
        overflow(y)
      else
        values(bin(k))(y)
    }

    def copy = new Binned(sub.copy, num, low, high, key, weighting)(values.map(_.copy), underflow.copy, overflow.copy)

    def +(that: Binned[DATUM, SUB]) = {
      if (that.num != this.num)
        throw new IllegalArgumentException(s"cannot add Binned because num differs (${this.num} vs ${that.num})")
      if (that.low != this.low)
        throw new IllegalArgumentException(s"cannot add Binned because low differs (${this.low} vs ${that.low})")
      if (that.high != this.high)
        throw new IllegalArgumentException(s"cannot add Binned because high differs (${this.high} vs ${that.high})")

      new Binned[DATUM, SUB](sub, num, low, high, key, weighting)(this.values zip that.values map {case (x, y) => x + y}, this.underflow + that.underflow, this.overflow + that.overflow)
    }

    def constructor = s"""${sub.name}(${sub.constructor}), $num, $low, $high"""
    def state = s"""Vector(${values.map(_.state).mkString(", ")}), ${underflow.state}, ${overflow.state}"""
  }
  object Binned {
    def apply[DATUM, SUB <: Aggregator[DATUM, SUB]](sub: SUB, num: Int, low: Double, high: Double, key: Weighted[DATUM] => Double, weighting: Weighting[DATUM] = noWeighting[DATUM]) =
      new Binned[DATUM, SUB](sub, num, low, high, key, weighting)()
  }
}
