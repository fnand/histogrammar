package org.dianahep.histogrammar

import scala.language.implicitConversions

import io.continuum.bokeh._


package object bokeh extends App with Tools {
  implicit def binnedToHistogramMethods(hist: Selected[Binned[Counted, Counted, Counted, Counted]]): HistogramMethods =
    new HistogramMethods(hist)

  implicit def binningToHistogramMethods[DATUM](hist: Selecting[DATUM, Binning[DATUM, Counting, Counting, Counting, Counting]]): HistogramMethods =
    new HistogramMethods(Factory.fromJson(hist.toJson).as[Selected[Binned[Counted, Counted, Counted, Counted]]])

  implicit def sparselyBinnedToHistogramMethods(hist: Selected[SparselyBinned[Counted, Counted]]): HistogramMethods =
    if (hist.value.numFilled > 0)
      new HistogramMethods(
        new Selected(hist.entries, hist.quantityName, new Binned(hist.value.low.get, hist.value.high.get, 0.0, hist.value.quantityName, hist.value.minBin.get to hist.value.maxBin.get map {i => new Counted(hist.value.at(i).flatMap(x => Some(x.entries)).getOrElse(0L))}, new Counted(0L), new Counted(0L), hist.value.nanflow))
      )
    else
      throw new RuntimeException("sparsely binned histogram has no entries")

  implicit def sparselyBinningToHistogramMethods[DATUM](hist: Selecting[DATUM, SparselyBinning[DATUM, Counting, Counting]]): HistogramMethods =
    sparselyBinnedToHistogramMethods(Factory.fromJson(hist.toJson).as[Selected[SparselyBinned[Counted, Counted]]])
}

package bokeh {
  class HistogramMethods(hist: Selected[Binned[Counted, Counted, Counted, Counted]]) {
  
    private def colorSelector(c: String) = c match {
       case "white" => Color.White
       case "black" => Color.Black
       case "red"   => Color.Red
       case other   => throw new IllegalArgumentException(
         s"Only white, black, red colors are supported but got $other.")
    }

    //This is 1D plot
    def plot(markerType: String = "circle", markerSize: Int = 1, fillColor: String = "white", lineColor: String = "black", xaxisLocation: Location = Location.Below, yaxisLocation: Location = Location.Left) : Document = {

      //Prepare histogram contents for plotting
      val h = hist.value.high
      val l = hist.value.low
      val step = (h-l)/hist.value.values.length

      object source extends ColumnDataSource {
         val x = column(l to h by step)
         val y = column(hist.value.values.map(_.entries))
      }

      import source.{x,y}

      val xdr = new DataRange1d
      val ydr = new DataRange1d

      val plot = new Plot().x_range(xdr).y_range(ydr).tools(Pan|WheelZoom)

      val xaxis = new LinearAxis().plot(plot).location(xaxisLocation)
      val yaxis = new LinearAxis().plot(plot).location(yaxisLocation)
      plot.below <<= (xaxis :: _)
      plot.left <<= (yaxis :: _)

      //Set marker color, fill color, line color
      //Note: here, line is an exterior of the marker
      //FIXME plotting options should be configrable!
      val glyph = MarkerFactory(markerType).x(x).y(y).size(markerSize).fill_color(colorSelector(fillColor)).line_color(colorSelector(lineColor))

      //FIXME renderer: not configurable for now
      val circle = new GlyphRenderer().data_source(source).glyph(glyph)

      plot.renderers := List(xaxis, yaxis, circle)

      new Document(plot)
    }

    def save(plot: Document, fname: String) : Any = {
      val html = plot.save(fname)
      println(s"Wrote ${html.file}. Open ${html.url} in a web browser.")
      html.view()
    }

  }
}
