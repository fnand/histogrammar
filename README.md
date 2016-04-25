# Histogrammar

## Quick links to reference documentation

  * [Scaladocs for the Scala version](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.package)
  * [Catalog of primitives](#catalog-of-primitives)
  * [Current status](#status)

## What is this and why might I want it?

Histogrammar is a declarative grammar for booking histograms with automatic filling and merging. It simplifies the process of reducing a huge, distributed dataset to a form that can be plotted, especially in a functional workflow like Apache Spark. It is a standardized set of routines available in many programming languages that interoperate by serializing to and from JSON. It also generalizes the concept of histogramming to an algebra of composable primitives, allowing them to be combined in novel ways.

That was the concise overview. Here's a simple example.

```scala
import org.dianahep.histogrammar._
import org.dianahep.histogrammar.histogram._

val px_histogram = Histogram(100, -5, 5, {mu: Muon => mu.px})
val pt_histogram = Histogram(80, 0, 8, {mu: Muon => Math.sqrt(mu.px*mu.px + mu.py*mu.py)})
val cut_histogram = Histogram(100, -5, 5, {mu: Muon => mu.px}, {mu: Muon => mu.py < 0.0})

val all_histograms = Label("px" -> px_histogram, "pt" -> pt_histogram, "cut" -> cut_histogram)

val final_histogram = rdd.aggregate(all_histograms)(new Increment, new Combine)

println(final_histogram("pt").ascii)
```

The `rdd.aggregate` function submits the histograms to Spark, which fills independent partial histograms in each of its worker nodes, combines partial results, and returns the final result for plotting. However, it required very little input from the user. In this example, `rdd` is a dataset of `Muon` objects, which the histograms view in different ways.

## Managing complexity

Most of the code in this snippet is concerned with booking the histograms (setting the range and number of bins) and describing how to fill them with data. The fill rule is given in each histogram's constructor, so that the data analyst doesn't have to maintain the booking in one part of the code, filling in another, and (for distributed jobs) combining in yet another part of the code. In a data analysis with hundreds of histograms, rather than three, this consolidation helps considerably.

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

Just as we could make directories by nesting the `Label` class, we should be able to make multivariate views of a dataset by nesting histograms. But to do that effectively, we have to decompose histograms into their fundamental components.

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

This aggregator divides the space by binning in one continuous variable, `X`, and then further subdivides it by binning in `Y` Then it counts. The result is a two-dimensional histogram, like both of the following:

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
                        Categorize({d: D => d.group}, value = Quantile({d: D => d.value}))))
```

([source: R](http://stackoverflow.com/questions/27012500/how-to-align-violin-plots-with-boxplots))

<img src="http://diana-hep.org/histogrammar/images/violin_and_box.png">

## Histogrammar does not produce graphics

In the discussion above, I included plots from many different plotting packages. Histogrammar is not a plotting package: it aggregates data and passes the result to your favorite plotter. Usually, the aggregation step is more computationally expensive than plotting, so it's frustrating to have to repeat a time-consuming aggregation just to change a cosmetic aspect of a plot. Aggregation and graphics must be kept separate.

Aggregation primitives are also easier to implement than graphics, so Histogrammar's core of primitives will be implemented in many different programming languages with a canonical JSON representation. A dataset aggregated in Scala can be plotted in Python. Most language-specific implementations recognize common patterns, such as bin-count being a one-dimensional histogram, to generate the appropriate plot.

## Catalog of primitives

| Primitive       | Description |
|:----------------|:------------|
| [Count](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Count$)           | Count data, ignoring their content. (Actually a sum of weights.) |
| [Sum](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Sum$)             | Accumulate the sum of a given quantity. |
| [Average](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Average$)         | Accumulate the weighted mean of a given quantity. |
| [Deviate](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Deviate$)         | Accumulate a weighted variance, mean, and total weight of a given quantity (using an algorithm that is stable for large numbers). |
| [AbsoluteErr](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.AbsoluteErr$)     | Accumulate the weighted Mean Absolute Error (MAE) of a quantity whose nominal value is zero. |
| [Minimize](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Minimize$)        | Find the minimum value of a given quantity. If no data are observed, the result is NaN. |
| [Maximize](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Maximize$)        | Find the maximum value of a given quantity. If no data are observed, the result is NaN. |
| [Quantile](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Quantile$)        | Accumulate an adaptively binned histogram to compute approximate quantiles, such as the median. |
| [Bag](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Bag$)             | Accumulate raw data up to an optional limit, at which point only the total number is preserved. |
| [Bin](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Bin$)             | Split a given quantity into equally spaced bins between specified limits and fill only one bin per datum. |
| [SparselyBin](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.SparselyBin$)     | Split a quantity into equally spaced bins, filling only one bin per datum and creating new bins as necessary. |
| [CentrallyBin](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.CentrallyBin$)    | Split a quantity into bins defined by a set of bin centers, filling only one datum per bin with no overflows or underflows. |
| [AdaptivelyBin](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.AdaptivelyBin$)   | Split a quanity into bins dynamically with a clustering algorithm, filling only one datum per bin with no overflows or underflows. |
| [Fraction](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Fraction$)        | Accumulate two containers, one with all data (denominator), and one with data that pass a given selection (numerator). |
| [Stack](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Stack$)           | Accumulate a suite containers, filling all that are above a given cut on a given expression. |
| [Partition](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Partition$)       | Accumulate a suite containers, filling the one that is between a pair of given cuts on a given expression. |
| [Categorize](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Categorize$)      | Split a given quantity by its categorical (string-based) value and fill only one category per datum. |
| [Label](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Label$)           | Accumulate any number of containers of the SAME type and label them with strings. Every one is filled with every input datum. |
| [UntypedLabel](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.UntypedLabel$)    | Accumulate containers of any type except Count and label them with strings. Every one is filled with every input datum. |
| [Index](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Index$)           | Accumulate any number of containers of the SAME type anonymously in a list. Every one is filled with every input datum. |
| [Branch](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.Branch$)          | Accumulate containers of DIFFERENT types, indexed by i0 through i9. Every one is filled with every input datum. |

## Status

Last released version was 0.2. The following refers to the git master branch.

| Primitive              | Scala | Python  | C++     | SQL     | R       | Javascript | CUDA/OpenCL |
|:-----------------------|:------|:--------|:--------|:--------|:--------|:-----------|:------------|
| Count                  | done  | done    |         |         |         |            |             |
| Sum                    | done  | done    |         |         |         |            |             |
| Average                | done  | done    |         |         |         |            |             |
| Deviate                | done  | done    |         |         |         |            |             |
| AbsoluteErr            | done  | done    |         |         |         |            |             |
| Minimize               | done  | done    |         |         |         |            |             |
| Maximize               | done  | done    |         |         |         |            |             |
| Quantile               | done  |         |         |         |         |            |             |
| Bag                    | done  | done    |         |         |         |            |             |
| Bin                    | done  | done    |         |         |         |            |             |
| SparselyBin            | done  | done    |         |         |         |            |             |
| CentrallyBin           | done  |         |         |         |         |            |             |
| AdaptivelyBin          | done  |         |         |         |         |            |             |
| IrregularlyBin         |       |         |         |         |         |            |             |
| Fraction               | done  | 6       |         |         |         |            |             |
| Stack                  | done  | 7       |         |         |         |            |             |
| Partition              | done  | 8       |         |         |         |            |             |
| Categorize             | done  | 5       |         |         |         |            |             |
| Label                  | done  | 1       |         |         |         |            |             |
| UntypedLabel           | done  | 2       |         |         |         |            |             |
| Index                  | done  | 3       |         |         |         |            |             |
| Branch                 | done  | 4       |         |         |         |            |             |
