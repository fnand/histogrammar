# Histogrammar

## Quick links to reference documentation

  * [Scaladocs for the Scala version](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.package)

## What is this and why might I want it?

Histogrammar is a declarative grammar for booking histograms with automatic filling and merging. It simplifies the process of reducing a huge, distributed dataset to a form that can be plotted, especially in a functional workflow like Apache Spark. It is a standardized set of routines available in many programming languages that interoperate by serializing to and from JSON. It also generalizes the concept of histogramming to an algebra of composable primitives, allowing them to be combined in novel ways.

That's the concise overview. Here's a simple example.

```scala
import org.dianahep.histogrammar._
import org.dianahep.histogrammar.histogram._

val px_histogram = Histogram(100, -5, 5, {mu: Muon => mu.px})
val pt_histogram = Histogram(80, 0, 8, {mu: Muon => Math.sqrt(mu.px*mu.px + mu.py*mu.py)})
val cut_histogram = Histogram(100, -5, 5, {mu: Muon => mu.px}, {mu: Muon => mu.py < 0.0})

val all_histograms = Label("px" -> px_histogram, "pt" -> pt_histogram, "cut" -> cut_histogram)

val final_histogram = rdd.aggregate(all_histograms)(new Increment, new Combine)
```

The last line submits the histograms to Spark, which fills independent partial histograms in each of its worker nodes, combines partial results, and returns the final result for plotting. However, it required very little input from the user. In this example, `rdd` is a dataset of `Muon` objects, which the histograms view in different ways.

## Managing complexity

Most of the code in this snippet is concerned with booking the histograms (setting the range and number of bins) and describing how to fill them with data. The fill rule is given in each histogram's constructor, so that the data analyst doesn't have to maintain the booking in one part of the code, filling in another, and (for distributed jobs) combining in yet another part of the code. In a data analysis with hundreds of histograms, rather than three, this consolidation helps to manage complexity.

The `all_histograms` object in the example above is a mapping from names to histograms. It, too, has a booking-incrementing-combining lifecycle, where its fill rule is to pass all of the data to all of its constituents. It is a sort of "meta-histogram," an aggregator of aggregators.

We could orgnize the histograms into directories by nesting them, because `Label` classes are composable:

```scala
val directories = Label("momentum" -> Label("px" -> ..., "pt" -> ...),
                        "position" -> Label("x" -> ..., "y" -> ...))
```

## Generalized nesting

But what if we wanted to make a histogram of histograms? That is, something like the following:

([source](https://cds.cern.ch/record/213816))

<img src="http://diana-hep.org/histogrammar/images/histograms_of_histograms.png">

The top row of plots show data in one region of _y_ (rapidity), the bottom show another; each column shows data in a different "bin" of centrality, and the plots themselves are histograms of _m_ (dimuon mass).

Just as we could make directories by nesting the `Label` class, we should be able to make multidimensional views of a dataset by nesting histograms. But to do that effectively, we have to decompose histograms into their fundamental components.

## What is a histogram, really?

A histogram is a summary of a dataset, produced by aggregation. There are other ways to summarize a dataset by aggregation: simply counting the number of entries, computing the sum of some quantity, computing a mean or standard deviation, etc. Each of these summarizes the data in a different way, conveying more or less information.

A histogram is a continuous variable that has been discretized (binned) and the number of entries in each bin are simply counted. Thus, we could write a histogram aggregator like this:

```scala
val histogram = Bin(numberOfBins, low, high, fillRule, value = Count())
```

The `Bin` is a container of sub-aggregators, much like `Label`, but its behavior is different. Now consider the following.

```scala
val h2d = Bin(numBinsX, lowX, highX, fillX, value = Bin(numBinsY, lowY, highY, fillY, value = Count())
```

This aggregator bins one continuous variable, `X`, and further subdivides the space by binning `Y` before counting. It is a two-dimensional histogram, like both of the following:

([left source: matplotlib](http://matplotlib.org/examples/pylab_examples/hist2d_log_demo.html)) ([right source: PAW](http://www.hepl.hiroshima-u.ac.jp/phx/sarupaw_html/hist.html))

<img src="http://diana-hep.org/histogrammar/images/two_dimensional.png" height="300px"> <img src="http://diana-hep.org/histogrammar/images/lego_plot.png" height="300px">

A so-called "profile plot" is a histogram in which each bin accumulates a mean and standard deviation, rather than a count. The Histogrammar primitive for mean and standard deviation is `Deviate`.

```scala
val profile = Bin(numBinsX, lowX, highX, fillRuleX, value = Deviate(fillRuleY))
```

([source: ROOT](https://root.cern.ch/root/htmldoc/guides/users-guide/ROOTUsersGuide.html))

<img src="http://diana-hep.org/histogrammar/images/profile_plot.png">

The appropriate set of primitives can make short work of many common plot types. Most of these are often assembled by hand.

```scala
val efficiency = Fraction({mu: Muon => mu.passesTrigger}, Histogram(120, -2.4, 2.4, {mu: Muon => mu.eta})
```

([source: ROOT](http://www.phys.ufl.edu/~jlow/znunuHbbTriggerStudies/triggerobjects.html)) 

<img src="http://diana-hep.org/histogrammar/images/efficiency.png" width="400px">

```scala
val efficiency2d = Fraction({mu: Muon => mu.passesTrigger},
                       Bin(100, -30, 30, {mu: Muon => mu.x}, value =
                           Bin(100, -60, 60, {mu: Muon => mu.y}, value = Count())))
```

([source: PAW](https://userweb.jlab.org/~fomin/scin/))

<img src="http://diana-hep.org/histogrammar/images/efficiency_2d.png">

Histogram bins turn a numerical feature into categories. But sometimes the data are already categorical.

```scala
val categorical_heatmap = Categorize({d: D => d.femaleReligion}, value =
                              Categorize({d: D => d.maleReligion}, value = Count())
```

([source: plot.ly](http://help.plot.ly/make-a-heatmap/))

<img src="http://diana-hep.org/histogrammar/images/categorical.png">

And that allows us to freely mix categorical and numerical aggregation.

```scala
val mixed = CategoricalStack(Histogram(140, 0, 140000, {d: D => d.salary}), {d: D => d.gender})
```

([source: SPSS](http://www.ibm.com/support/knowledgecenter/SSLVMB_20.0.0/com.ibm.spss.statistics.help/gpl_examples_barcharts_histogram_stack.htm))

<img src="http://diana-hep.org/histogrammar/images/stacked.png">

It also lets us swap one binning strategy with another without affecting anything else in the hierarchy.

  * **Bin:** user specifies number of bins, low edge, and high edge; bins are regularly spaced.
  * **SparselyBin:** user specifies bin width; bins are filled when non-zero using a hash-map (still regularly spaced).
  * **CentrallyBin:** user specifies bin centers, which may be irregularly spaced. Membership is determined by closest center, which makes histogramming a close analogue of clustering in one dimension.
  * **AdaptivelyBin:** user specifies nothing; optimal bin placement is determined by a clustering algorithm.

The last is particularly useful for exploratory analysis: you want to make a plot to understand the distribution of your data, but specifying bins relies on prior knowledge of that distribution. It is also an essential ingredient in estimating medians and quartiles for box-and-whiskers plots, or mini-histograms for violin plots.

```scala
val violin_box = Branch(Categorize({d: D => d.group}, value = AdaptivelyBin({d: D => d.value}),
                        Categorize({d: D => d.group}, value = Quantile({d: D => d.group}))))
```

([source: R](http://stackoverflow.com/questions/27012500/how-to-align-violin-plots-with-boxplots))

<img src="http://diana-hep.org/histogrammar/images/violin_and_box.png">






