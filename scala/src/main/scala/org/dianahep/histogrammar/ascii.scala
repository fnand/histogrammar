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

/** Methods for drawing familiar combinations of containers, such as histograms, in ASCII art. */
package object ascii {
  //////////////////////////////////////////////////////////////// methods for Histogram and SparselyHistogram

  implicit def binnedToHistogramMethodsAscii(hist: Binned[Counted, Counted, Counted, Counted]): HistogramMethodsAscii =
    new HistogramMethodsAscii(binnedToHistogramMethods(hist).selected)
  implicit def binningToHistogramMethodsAscii[DATUM](hist: Binning[DATUM, Counting, Counting, Counting, Counting]): HistogramMethodsAscii =
    new HistogramMethodsAscii(binningToHistogramMethods(hist).selected)
  implicit def selectedBinnedToHistogramMethodsAscii(hist: Selected[Binned[Counted, Counted, Counted, Counted]]): HistogramMethodsAscii =
    new HistogramMethodsAscii(selectedBinnedToHistogramMethods(hist).selected)
  implicit def selectingBinningToHistogramMethodsAscii[DATUM](hist: Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]): HistogramMethodsAscii =
    new HistogramMethodsAscii(selectingBinningToHistogramMethods(hist).selected)
  implicit def sparselyBinnedToHistogramMethodsAscii(hist: SparselyBinned[Counted, Counted]): HistogramMethodsAscii =
    new HistogramMethodsAscii(sparselyBinnedToHistogramMethods(hist).selected)
  implicit def sparselyBinningToHistogramMethodsAscii[DATUM](hist: SparselyBinning[DATUM, Counting, Counting]): HistogramMethodsAscii =
    new HistogramMethodsAscii(sparselyBinningToHistogramMethods(hist).selected)
  implicit def selectedSparselyBinnedToHistogramMethodsAscii(hist: Selected[SparselyBinned[Counted, Counted]]): HistogramMethodsAscii =
    new HistogramMethodsAscii(selectedSparselyBinnedToHistogramMethods(hist).selected)
  implicit def selectingSparselyBinningToHistogramMethodsAscii[DATUM](hist: Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]): HistogramMethodsAscii =
    new HistogramMethodsAscii(selectingSparselyBinningToHistogramMethods(hist).selected)

  class HistogramMethodsAscii(val selected: Selected[Binned[Counted, Counted, Counted, Counted]]) {
    /** ASCII representation of a histogram for debugging on headless systems. Limited to 80 columns. */
    def ascii: String = ascii(80)
    /** ASCII representation of a histogram for debugging on headless systems. Limited to `width` columns. */
    def ascii(width: Int): String = {
      val binned = selected.value

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

  //////////////////////////////////////////////////////////////// methods for Profile and SparselyProfile

  implicit def binnedToProfileMethodsAscii(hist: Binned[Deviated, Counted, Counted, Counted]): ProfileMethodsAscii =
    new ProfileMethodsAscii(binnedToProfileMethods(hist).selected)
  implicit def binningToProfileMethodsAscii[DATUM](hist: Binning[DATUM, Deviating[DATUM], Counting, Counting, Counting]): ProfileMethodsAscii =
    new ProfileMethodsAscii(binningToProfileMethods(hist).selected)
  implicit def selectedBinnedToProfileMethodsAscii(hist: Selected[Binned[Deviated, Counted, Counted, Counted]]): ProfileMethodsAscii =
    new ProfileMethodsAscii(selectedBinnedToProfileMethods(hist).selected)
  implicit def selectingBinningToProfileMethodsAscii[DATUM](hist: Selecting[DATUM, Binning[DATUM, Deviating[DATUM], Counting, Counting, Counting]]): ProfileMethodsAscii =
    new ProfileMethodsAscii(selectingBinningToProfileMethods(hist).selected)
  implicit def sparselyBinnedToProfileMethodsAscii(hist: SparselyBinned[Deviated, Counted]): ProfileMethodsAscii =
    new ProfileMethodsAscii(sparselyBinnedToProfileMethods(hist).selected)
  implicit def sparselyBinningToProfileMethodsAscii[DATUM](hist: SparselyBinning[DATUM, Deviating[DATUM], Counting]): ProfileMethodsAscii =
    new ProfileMethodsAscii(sparselyBinningToProfileMethods(hist).selected)
  implicit def selectedSparselyBinnedToProfileMethodsAscii(hist: Selected[SparselyBinned[Deviated, Counted]]): ProfileMethodsAscii =
    new ProfileMethodsAscii(selectedSparselyBinnedToProfileMethods(hist).selected)
  implicit def selectingSparselyBinningToProfileMethodsAscii[DATUM](hist: Selecting[DATUM, SparselyBinning[DATUM, Deviating[DATUM], Counting]]): ProfileMethodsAscii =
    new ProfileMethodsAscii(selectingSparselyBinningToProfileMethods(hist).selected)

  class ProfileMethodsAscii(val selected: Selected[Binned[Deviated, Counted, Counted, Counted]]) {
    /** ASCII representation of a histogram for debugging on headless systems. Limited to 80 columns. */
    def ascii: String = ascii(80)
    /** ASCII representation of a histogram for debugging on headless systems. Limited to `width` columns. */
    def ascii(width: Int): String = {
      val binned = selected.binned

      val minValue = binned.values.map(dev => dev.mean - Math.sqrt(dev.variance)).min
      val maxValue = binned.values.map(dev => dev.mean + Math.sqrt(dev.variance)).max
      val range = maxValue - minValue
      val minEdge = minValue - 0.1*range
      val maxEdge = maxValue + 0.1*range

      val binWidth = (binned.high - binned.low) / binned.values.size
      def sigfigs(x: Double, n: Int) = new java.math.BigDecimal(x).round(new java.math.MathContext(n)).toString

      val prefixValues = binned.values.zipWithIndex map {case (v, i) =>
        (i * binWidth + binned.low, (i + 1) * binWidth + binned.low, v.mean, Math.sqrt(v.variance))
      }
      val prefixValuesStr = prefixValues map {case (binlow, binhigh, mean, stdev) => (sigfigs(Math.abs(binlow), 3), sigfigs(Math.abs(binhigh), 3), sigfigs(Math.abs(mean), 4), sigfigs(Math.abs(stdev), 4))}

      val widestBinlow = Math.max(prefixValuesStr.map(_._1.size).max, 2)
      val widestBinhigh = Math.max(prefixValuesStr.map(_._2.size).max, 2)
      val widestMean = Math.max(prefixValuesStr.map(_._3.size).max, 2)
      val widestStdev = Math.max(prefixValuesStr.map(_._4.size).max, 2)
      val formatter = s"[ %s%-${widestBinlow}s, %s%-${widestBinhigh}s) %s%-${widestMean}s +- %s%-${widestStdev}s "
      val prefixWidth = widestBinlow + widestBinhigh + widestMean + widestStdev + 14

      val reducedWidth = width - prefixWidth
      val zeroIndex = Math.round(reducedWidth * (0.0 - minEdge) / (maxEdge - minEdge)).toInt
      val zeroLine1 = " " * prefixWidth + " " + f"$minEdge%-10g" + " " + (0 until (reducedWidth - 20)).map({i =>
        if (i + 10 == zeroIndex)
          "0"
        else
          " "
      }).mkString + " " + f"$maxEdge%10g"
      val zeroLine2 = " " * prefixWidth + " " + "+" + (0 until (reducedWidth - 1)).map({i =>
        if (i == zeroIndex)
          "+"
        else
          "-"
      }).mkString + "-" + "+"

      val lines = binned.values zip prefixValues zip prefixValuesStr map {case ((v, (binlow, binhigh, mean, stdev)), (binlowAbs, binhighAbs, meanAbs, stdevAbs)) =>
        val binlowSign = if (binlow < 0) "-" else " "
        val binhighSign = if (binhigh < 0) "-" else " "
        val meanSign = if (mean < 0) "-" else " "
        val stdevSign = if (stdev < 0) "-" else " "

        val botIndex = Math.round(reducedWidth * (v.mean - Math.sqrt(v.variance) - minEdge) / (maxEdge - minEdge)).toInt
        val midIndex = Math.round(reducedWidth * (v.mean                         - minEdge) / (maxEdge - minEdge)).toInt
        val topIndex = Math.round(reducedWidth * (v.mean + Math.sqrt(v.variance) - minEdge) / (maxEdge - minEdge)).toInt

        formatter.format(binlowSign, binlowAbs, binhighSign, binhighAbs, meanSign, meanAbs, stdevSign, stdevAbs) + "|" + (0 until reducedWidth).map({i =>
          if (i == zeroIndex)
            "|"
          else if (i < botIndex  ||  i > topIndex)
            " "
          else if (i == midIndex)
            "+"
          else if (i == botIndex  ||  i == topIndex)
            "|"
          else
            "-"
        }).mkString + "|"
      }

      (List(zeroLine1, zeroLine2) ++ lines ++ List(zeroLine2)).mkString("\n")      
    }
  }

  //////////////////////////////////////////////////////////////// methods for StackedHistogram, including cases for mixed tenses

  implicit def binnedToStackedHistogramMethodsAscii(hist: Stacked[Binned[Counted, Counted, Counted, Counted]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(binnedToStackedHistogramMethods(hist).stacked)
  implicit def binningToStackedHistogramMethodsAscii[DATUM](hist: Stacking[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(binningToStackedHistogramMethods(hist).stacked)
  implicit def selectedBinnedToStackedHistogramMethodsAscii(hist: Stacked[Selected[Binned[Counted, Counted, Counted, Counted]]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(selectedBinnedToStackedHistogramMethods(hist).stacked)
  implicit def selectingBinningToStackedHistogramMethodsAscii[DATUM](hist: Stacking[DATUM, Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(selectingBinningToStackedHistogramMethods(hist).stacked)
  implicit def sparselyBinnedToStackedHistogramMethodsAscii(hist: Stacked[SparselyBinned[Counted, Counted]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(sparselyBinnedToStackedHistogramMethods(hist).stacked)
  implicit def sparselyBinningToStackingHistogramMethodsAscii[DATUM](hist: Stacking[DATUM, SparselyBinning[DATUM, Counting, Counting]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(sparselyBinningToStackingHistogramMethods(hist).stacked)
  implicit def selectedSparselyBinnedToStackedHistogramMethodsAscii(hist: Stacked[Selected[SparselyBinned[Counted, Counted]]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(selectedSparselyBinnedToStackedHistogramMethods(hist).stacked)
  implicit def selectingSparselyBinningToStackedHistogramMethodsAscii[DATUM](hist: Stacking[DATUM, Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(selectingSparselyBinningToStackedHistogramMethods(hist).stacked)
  implicit def binnedMixedToStackedHistogramMethodsAscii[DATUM](hist: Stacked[Binning[DATUM, Counting, Counting, Counting, Counting]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(binnedMixedToStackedHistogramMethods(hist).stacked)
  implicit def selectedBinnedMixedToStackedHistogramMethodsAscii[DATUM](hist: Stacked[Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(selectedBinnedMixedToStackedHistogramMethods(hist).stacked)
  implicit def sparselyBinnedMixedToStackedHistogramMethodsAscii[DATUM](hist: Stacked[SparselyBinning[DATUM, Counting, Counting]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(sparselyBinnedMixedToStackedHistogramMethods(hist).stacked)
  implicit def selectedSparselyBinnedMixedToStackedHistogramMethodsAscii[DATUM](hist: Stacked[Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]]): StackedHistogramMethodsAscii =
    new StackedHistogramMethodsAscii(selectedSparselyBinnedMixedToStackedHistogramMethods(hist).stacked)

  class StackedHistogramMethodsAscii(val stacked: Stacked[Selected[Binned[Counted, Counted, Counted, Counted]]])

  //////////////////////////////////////////////////////////////// methods for PartitionedHistogram

  implicit def binnedToPartitionedHistogramMethodsAscii(hist: Partitioned[Binned[Counted, Counted, Counted, Counted]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(binnedToPartitionedHistogramMethods(hist).partitioned)
  implicit def binningToPartitionedHistogramMethodsAscii[DATUM](hist: Partitioning[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(binningToPartitionedHistogramMethods(hist).partitioned)
  implicit def selectedBinnedToPartitionedHistogramMethodsAscii(hist: Partitioned[Selected[Binned[Counted, Counted, Counted, Counted]]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(selectedBinnedToPartitionedHistogramMethods(hist).partitioned)
  implicit def selectingBinningToPartitionedHistogramMethodsAscii[DATUM](hist: Partitioning[DATUM, Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(selectingBinningToPartitionedHistogramMethods(hist).partitioned)
  implicit def sparselyBinnedToPartitionedHistogramMethodsAscii(hist: Partitioned[SparselyBinned[Counted, Counted]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(sparselyBinnedToPartitionedHistogramMethods(hist).partitioned)
  implicit def sparselyBinningToPartitioningHistogramMethodsAscii[DATUM](hist: Partitioning[DATUM, SparselyBinning[DATUM, Counting, Counting]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(sparselyBinningToPartitioningHistogramMethods(hist).partitioned)
  implicit def selectedSparselyBinnedToPartitionedHistogramMethodsAscii(hist: Partitioned[Selected[SparselyBinned[Counted, Counted]]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(selectedSparselyBinnedToPartitionedHistogramMethods(hist).partitioned)
  implicit def selectingSparselyBinningToPartitionedHistogramMethodsAscii[DATUM](hist: Partitioning[DATUM, Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]]): PartitionedHistogramMethodsAscii =
    new PartitionedHistogramMethodsAscii(selectingSparselyBinningToPartitionedHistogramMethods(hist).partitioned)

  class PartitionedHistogramMethodsAscii(val partitioned: Partitioned[Selected[Binned[Counted, Counted, Counted, Counted]]])

  //////////////////////////////////////////////////////////////// methods for FractionedHistogram

  implicit def binnedToFractionedHistogramMethodsAscii(hist: Fractioned[Binned[Counted, Counted, Counted, Counted]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(binnedToFractionedHistogramMethods(hist).fractioned)
  implicit def binningToFractionedHistogramMethodsAscii[DATUM](hist: Fractioning[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(binningToFractionedHistogramMethods(hist).fractioned)
  implicit def selectedBinnedToFractionedHistogramMethodsAscii(hist: Fractioned[Selected[Binned[Counted, Counted, Counted, Counted]]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(selectedBinnedToFractionedHistogramMethods(hist).fractioned)
  implicit def selectingBinningToFractionedHistogramMethodsAscii[DATUM](hist: Fractioning[DATUM, Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(selectingBinningToFractionedHistogramMethods(hist).fractioned)
  implicit def sparselyBinnedToFractionedHistogramMethodsAscii(hist: Fractioned[SparselyBinned[Counted, Counted]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(sparselyBinnedToFractionedHistogramMethods(hist).fractioned)
  implicit def sparselyBinningToFractioningHistogramMethodsAscii[DATUM](hist: Fractioning[DATUM, SparselyBinning[DATUM, Counting, Counting]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(sparselyBinningToFractioningHistogramMethods(hist).fractioned)
  implicit def selectedSparselyBinnedToFractionedHistogramMethodsAscii(hist: Fractioned[Selected[SparselyBinned[Counted, Counted]]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(selectedSparselyBinnedToFractionedHistogramMethods(hist).fractioned)
  implicit def selectingSparselyBinningToFractionedHistogramMethodsAscii[DATUM](hist: Fractioning[DATUM, Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]]): FractionedHistogramMethodsAscii =
    new FractionedHistogramMethodsAscii(selectingSparselyBinningToFractionedHistogramMethods(hist).fractioned)

  class FractionedHistogramMethodsAscii(val fractioned: Fractioned[Selected[Binned[Counted, Counted, Counted, Counted]]])

}
