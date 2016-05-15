// Copyright 2016 Jim Pivarski
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//     http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.dianahep.histogrammar

import scala.collection.mutable
import scala.language.implicitConversions

import org.dianahep.histogrammar._

/** Specialty methods for container combinations that look like histograms. */
package object histogram {
  /** Type alias for conventional histograms (filled). */
  type Histogrammed = Cutted[Binned[Counted, Counted, Counted, Counted]]
  /** Type alias for conventional histograms (filling). */
  type Histogramming[DATUM] = Cutting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]
  /** Convenience function for creating a conventional histogram. */
  def Histogram[DATUM]
    (num: Int,
      low: Double,
      high: Double,
      quantity: UserFcn[DATUM, Double],
      selection: UserFcn[DATUM, Double] = unweighted[DATUM]) =
    Cut(selection, Bin(num, low, high, quantity))

  /** Type alias for sparsely binned histograms (filled). */
  type SparselyHistogrammed = Cutted[SparselyBinned[Counted, Counted]]
  /** Type alias for sparsely binned histograms (filling). */
  type SparselyHistogramming[DATUM] = Cutting[DATUM, SparselyBinning[DATUM, Counting, Counting]]
  /** Convenience function for creating a sparsely binned histogram. */
  def SparselyHistogram[DATUM]
    (binWidth: Double,
      quantity: UserFcn[DATUM, Double],
      selection: UserFcn[DATUM, Double] = unweighted[DATUM],
      origin: Double = 0.0) =
    Cut(selection, SparselyBin(binWidth, quantity, origin = origin))

  implicit def binnedToHistogramMethods(hist: Cutted[Binned[Counted, Counted, Counted, Counted]]): HistogramMethods =
    new HistogramMethods(hist)

  implicit def binningToHistogramMethods[DATUM](hist: Cutting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]): HistogramMethods =
    new HistogramMethods(Factory.fromJson(hist.toJson).as[Cutted[Binned[Counted, Counted, Counted, Counted]]])

  implicit def sparselyBinnedToHistogramMethods(hist: Cutted[SparselyBinned[Counted, Counted]]): HistogramMethods =
    if (hist.value.numFilled > 0)
      new HistogramMethods(
        new Cutted(hist.entries, hist.selectionName, new Binned(hist.value.low.get, hist.value.high.get, 0.0, hist.value.quantityName, hist.value.minBin.get to hist.value.maxBin.get map {i => new Counted(hist.value.at(i).flatMap(x => Some(x.entries)).getOrElse(0L))}, new Counted(0L), new Counted(0L), hist.value.nanflow))
      )
    else
      throw new RuntimeException("sparsely binned histogram has no entries")

  implicit def sparselyBinningToHistogramMethods[DATUM](hist: Cutting[DATUM, SparselyBinning[DATUM, Counting, Counting]]): HistogramMethods =
    sparselyBinnedToHistogramMethods(Factory.fromJson(hist.toJson).as[Cutted[SparselyBinned[Counted, Counted]]])
}

package histogram {
  /** Methods that are implicitly added to container combinations that look like histograms. */
  class HistogramMethods(hist: Cutted[Binned[Counted, Counted, Counted, Counted]]) {
    def binned = hist.value

    /** Bin values as numbers, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalValues: Seq[Double] = binned.values.map(_.entries)
    /** Overflow as a number, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalOverflow: Double = binned.overflow.entries
    /** Underflow as a number, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalUnderflow: Double = binned.underflow.entries
    /** Nanflow as a number, rather than [[org.dianahep.histogrammar.Counted]]/[[org.dianahep.histogrammar.Counting]]. */
    def numericalNanflow: Double = binned.nanflow.entries

    /** ASCII representation of a histogram for debugging on headless systems. Limited to 80 columns. */
    def ascii: String = ascii(80)
    /** ASCII representation of a histogram for debugging on headless systems. Limited to `width` columns. */
    def ascii(width: Int): String = {
      val minCount = Math.min(Math.min(Math.min(binned.values.map(_.entries).min, binned.overflow.entries), binned.underflow.entries), binned.nanflow.entries)
      val maxCount = Math.max(Math.max(Math.max(binned.values.map(_.entries).max, binned.overflow.entries), binned.underflow.entries), binned.nanflow.entries)
      val range = maxCount - minCount
      val minEdge = if (minCount < 0.0) minCount - 0.1*range else 0.0
      val maxEdge = maxCount + 0.1*range

      val binWidth = (binned.high - binned.low) / binned.values.size
      def sigfigs(x: Double, n: Int) = new java.math.BigDecimal(x).round(new java.math.MathContext(n)).toString

      val prefixValues = binned.values.zipWithIndex map {case (v, i) =>
        (i * binWidth + binned.low, (i + 1) * binWidth + binned.low, v.entries)
      }
      val prefixValuesStr = prefixValues map {case (binlow, binhigh, entries) => (sigfigs(Math.abs(binlow), 3), sigfigs(Math.abs(binhigh), 3), sigfigs(Math.abs(entries), 4))}

      val widestBinlow = Math.max(prefixValuesStr.map(_._1.size).max, 2)
      val widestBinhigh = Math.max(prefixValuesStr.map(_._2.size).max, 2)
      val widestValue = Math.max(prefixValuesStr.map(_._3.size).max, 2)
      val formatter = s"[ %s%-${widestBinlow}s, %s%-${widestBinhigh}s) %s%-${widestValue}s "
      val prefixWidth = widestBinlow + widestBinhigh + widestValue + 9

      val reducedWidth = width - prefixWidth
      val zeroIndex = Math.round(reducedWidth * (0.0 - minEdge) / (maxEdge - minEdge)).toInt
      val zeroLine1 = " " * prefixWidth + " " + (if (zeroIndex > 0) " " else "") + " " * zeroIndex + "0" + " " * (reducedWidth - zeroIndex - 10) + " " + f"$maxEdge%10g"
      val zeroLine2 = " " * prefixWidth + " " + (if (zeroIndex > 0) "+" else "") + "-" * zeroIndex + "+" + "-" * (reducedWidth - zeroIndex - 1) + "-" + "+"

      val lines = binned.values zip prefixValues zip prefixValuesStr map {case ((v, (binlow, binhigh, value)), (binlowAbs, binhighAbs, valueAbs)) =>
        val binlowSign = if (binlow < 0) "-" else " "
        val binhighSign = if (binhigh < 0) "-" else " "
        val valueSign = if (value < 0) "-" else " "
        val peakIndex = Math.round(reducedWidth * (v.entries - minEdge) / (maxEdge - minEdge)).toInt
        if (peakIndex < zeroIndex)
          formatter.format(binlowSign, binlowAbs, binhighSign, binhighAbs, valueSign, valueAbs) + (if (zeroIndex > 0) "|" else "") + " " * peakIndex + "*" * (zeroIndex - peakIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          formatter.format(binlowSign, binlowAbs, binhighSign, binhighAbs, valueSign, valueAbs) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (peakIndex - zeroIndex) + " " * (reducedWidth - peakIndex) + "|"
      }

      val underflowIndex = Math.round(reducedWidth * (binned.underflow.entries - minEdge) / (maxEdge - minEdge)).toInt
      val underflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val underflowLine =
        if (underflowIndex < zeroIndex)
          underflowFormatter.format("underflow", sigfigs(binned.underflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * underflowIndex + "*" * (zeroIndex - underflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          underflowFormatter.format("underflow", sigfigs(binned.underflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (underflowIndex - zeroIndex) + " " * (reducedWidth - underflowIndex) + "|"

      val overflowIndex = Math.round(reducedWidth * (binned.overflow.entries - minEdge) / (maxEdge - minEdge)).toInt
      val overflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val overflowLine =
        if (overflowIndex < zeroIndex)
          overflowFormatter.format("overflow", sigfigs(binned.overflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * overflowIndex + "*" * (zeroIndex - overflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          overflowFormatter.format("overflow", sigfigs(binned.overflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (overflowIndex - zeroIndex) + " " * (reducedWidth - overflowIndex) + "|"

      val nanflowIndex = Math.round(reducedWidth * (binned.nanflow.entries - minEdge) / (maxEdge - minEdge)).toInt
      val nanflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val nanflowLine =
        if (nanflowIndex < zeroIndex)
          nanflowFormatter.format("nanflow", sigfigs(binned.nanflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * nanflowIndex + "*" * (zeroIndex - nanflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          nanflowFormatter.format("nanflow", sigfigs(binned.nanflow.entries, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (nanflowIndex - zeroIndex) + " " * (reducedWidth - nanflowIndex) + "|"

      (List(zeroLine1, zeroLine2, underflowLine) ++ lines ++ List(overflowLine, nanflowLine, zeroLine2)).mkString("\n")      
    }
  }
}
