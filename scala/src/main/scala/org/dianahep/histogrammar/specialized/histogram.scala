package org.dianahep.histogrammar.specialized

import scala.language.implicitConversions

import org.dianahep.histogrammar._

package object histogram {
  def Histogram[DATUM](num: Int, low: Double, high: Double, key: NumericalFcn[DATUM], selection: Selection[DATUM] = unweighted[DATUM]) =
    Bin[DATUM, Counted, Counted, Counted, Counted](num, low, high, key, selection)
  type Histogrammed = Binned[Counted, Counted, Counted, Counted]
  type Histogramming[DATUM] = Binning[DATUM, Counted, Counted, Counted, Counted]

  implicit def binnedToHistogramMethods(hist: Binned[Counted, Counted, Counted, Counted]): HistogramMethods =
    new HistogramMethods(hist)
  implicit def binningToHistogramMethods[DATUM](hist: Binning[DATUM, Counted, Counted, Counted, Counted]): HistogramMethods =
    new HistogramMethods(hist.fix)
  implicit def sparselyBinnedToHistogramMethods(hist: SparselyBinned[Counted, Counted]): HistogramMethods =
    new HistogramMethods(
      new Binned(hist.low, hist.high, hist.minBin to hist.maxBin map {i => Count.ed(hist.at(i).flatMap(x => Some(x.value)).getOrElse(0.0))}, Count.ed(0.0), Count.ed(0.0), hist.nanflow)
    )
  implicit def sparselyBinningToHistogramMethods[DATUM](hist: SparselyBinning[DATUM, Counted, Counted]): HistogramMethods =
    sparselyBinnedToHistogramMethods(hist.fix)
}

package histogram {
  class HistogramMethods(hist: Binned[Counted, Counted, Counted, Counted]) {
    def numericValues: Seq[Double] = hist.values.map(_.value)
    def numericOverflow: Double = hist.overflow.value
    def numericUnderflow: Double = hist.underflow.value
    def numericNanflow: Double = hist.nanflow.value

    def ascii: String = ascii(80)
    def ascii(width: Int): String = {
      val minCount = hist.values.map(_.value).min
      val maxCount = hist.values.map(_.value).max
      val range = maxCount - minCount
      val minEdge = if (minCount < 0.0) minCount - 0.1*range else 0.0
      val maxEdge = maxCount + 0.1*range

      val binWidth = (hist.high - hist.low) / hist.values.size
      def sigfigs(x: Double, n: Int) = new java.math.BigDecimal(x).round(new java.math.MathContext(n)).toString

      val prefixValues = hist.values.zipWithIndex map {case (v, i) =>
        (i * binWidth + hist.low, (i + 1) * binWidth + hist.low, v.value)
      }
      val prefixValuesStr = prefixValues map {case (binlow, binhigh, value) => (sigfigs(Math.abs(binlow), 3), sigfigs(Math.abs(binhigh), 3), sigfigs(Math.abs(value), 4))}

      val widestBinlow = Math.max(prefixValuesStr.map(_._1.size).max, 2)
      val widestBinhigh = Math.max(prefixValuesStr.map(_._2.size).max, 2)
      val widestValue = Math.max(prefixValuesStr.map(_._3.size).max, 2)
      val formatter = s"[ %s%-${widestBinlow}s, %s%-${widestBinhigh}s) %s%-${widestValue}s "
      val prefixWidth = widestBinlow + widestBinhigh + widestValue + 9

      val reducedWidth = width - prefixWidth
      val zeroIndex = Math.round(reducedWidth * (0.0 - minEdge) / (maxEdge - minEdge)).toInt
      val zeroLine1 = " " * prefixWidth + " " + (if (zeroIndex > 0) " " else "") + " " * zeroIndex + "0" + " " * (reducedWidth - zeroIndex - 10) + " " + f"$maxEdge%10g"
      val zeroLine2 = " " * prefixWidth + " " + (if (zeroIndex > 0) "+" else "") + "-" * zeroIndex + "+" + "-" * (reducedWidth - zeroIndex - 1) + "-" + "+"

      val lines = hist.values zip prefixValues zip prefixValuesStr map {case ((v, (binlow, binhigh, value)), (binlowAbs, binhighAbs, valueAbs)) =>
        val binlowSign = if (binlow < 0) "-" else " "
        val binhighSign = if (binhigh < 0) "-" else " "
        val valueSign = if (value < 0) "-" else " "
        val peakIndex = Math.round(reducedWidth * (v.value - minEdge) / (maxEdge - minEdge)).toInt
        if (peakIndex < zeroIndex)
          formatter.format(binlowSign, binlowAbs, binhighSign, binhighAbs, valueSign, valueAbs) + (if (zeroIndex > 0) "|" else "") + " " * peakIndex + "*" * (zeroIndex - peakIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          formatter.format(binlowSign, binlowAbs, binhighSign, binhighAbs, valueSign, valueAbs) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (peakIndex - zeroIndex) + " " * (reducedWidth - peakIndex) + "|"
      }

      val underflowIndex = Math.round(reducedWidth * (hist.underflow.value - minEdge) / (maxEdge - minEdge)).toInt
      val underflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val underflowLine =
        if (underflowIndex < zeroIndex)
          underflowFormatter.format("underflow", sigfigs(hist.underflow.value, 4)) + (if (zeroIndex > 0) "|" else "") + " " * underflowIndex + "*" * (zeroIndex - underflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          underflowFormatter.format("underflow", sigfigs(hist.underflow.value, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (underflowIndex - zeroIndex) + " " * (reducedWidth - underflowIndex) + "|"

      val overflowIndex = Math.round(reducedWidth * (hist.overflow.value - minEdge) / (maxEdge - minEdge)).toInt
      val overflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val overflowLine =
        if (overflowIndex < zeroIndex)
          overflowFormatter.format("overflow", sigfigs(hist.overflow.value, 4)) + (if (zeroIndex > 0) "|" else "") + " " * overflowIndex + "*" * (zeroIndex - overflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          overflowFormatter.format("overflow", sigfigs(hist.overflow.value, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (overflowIndex - zeroIndex) + " " * (reducedWidth - overflowIndex) + "|"

      val nanflowIndex = Math.round(reducedWidth * (hist.nanflow.value - minEdge) / (maxEdge - minEdge)).toInt
      val nanflowFormatter = s"%-${widestBinlow + widestBinhigh + 5}s    %-${widestValue}s "
      val nanflowLine =
        if (nanflowIndex < zeroIndex)
          nanflowFormatter.format("nanflow", sigfigs(hist.nanflow.value, 4)) + (if (zeroIndex > 0) "|" else "") + " " * nanflowIndex + "*" * (zeroIndex - nanflowIndex) + "|" + " " * (reducedWidth - zeroIndex) + "|"
        else
          nanflowFormatter.format("nanflow", sigfigs(hist.nanflow.value, 4)) + (if (zeroIndex > 0) "|" else "") + " " * zeroIndex + "|" + "*" * (nanflowIndex - zeroIndex) + " " * (reducedWidth - nanflowIndex) + "|"

      (List(zeroLine1, zeroLine2, underflowLine) ++ lines ++ List(overflowLine, nanflowLine, zeroLine2)).mkString("\n")      
    }
  }
}