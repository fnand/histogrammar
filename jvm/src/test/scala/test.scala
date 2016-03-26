package test.scala.histogrammar

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers

import org.dianahep.histogrammar._

class DefaultSuite extends FlatSpec with Matchers {
  "stuff" must "work" in {
    // def histogram[DATUM](num: Int, low: Double, high: Double, key: Weighted[DATUM] => Double, weighting: Weighting[Weighted[DATUM]] = unweighted) = Binned(Count[DATUM], num, low, high, key, weighting)

    case class Datum(one: Int, two: Double, three: String)

    // val hist = histogram(10, 5.0, 15.0, {d: Datum => d.one + d.two})

    val hist = Binned(Count[Datum], 10, 5.0, 15.0, {d: Datum => d.one + d.two})
    println(hist)

    hist(Datum(3, 3.3, "three"))
    println(hist)

    hist(Datum(3, 3.3, "three"))
    println(hist)

    hist(Datum(6, 6.6, "four"))
    println(hist)

    hist(Datum(6, 6.6, "four"))
    println(hist)

    hist(Datum(6, 6.6, "four"))
    println(hist)


  }
}
