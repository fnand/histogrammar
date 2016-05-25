# Histogrammar

## Introduction

Histogrammar is an experiment in aggregating data with functional primitives. It serves the same need as HBOOK and its descendants--- summarizing a large dataset with discretized distributions--- but it does so using composable aggregators instead of fixed histogram types.

For instance, to book and fill a histogram in [ROOT](http://root.cern.ch), you would do this:

    histogram = ROOT.TH1F("name", "title", 100, 0, 10)
    for muon in muons:
        if muon.pt > 10:
            histogram.fill(muon.mass)

But in histogrammar, you could do it like this:

    histogram = Select(lambda mu: mu.pt > 10, Bin(100, 0, 10, lambda mu: mu.mass, Count()))
    for muon in muons:
        histogram.fill(muon)

because a filtered histogram is just a selection on binned counting. To track means and standard deviations of track quality in each bin instead of counting (a "profile plot" instead of a 1-d histogram), you'd change the above to

    histogram = Select(lambda mu: mu.pt > 10,
                    Bin(100, 0, 10, lambda mu: mu.mass,
                        Deviate(lambda mu: mu.trackQuality)))

To make a 2-d histogram of `px` and `py` instead of a 1-d histogram, you'd do

    histogram = Bin(100, 0, 50, lambda mu: mu.px,
                    Bin(100, 0, 50, lambda mu: mu.py,
                        Count()))

and so on. A good set of primitives and the right lambda functions can replace specialized logic in the for loop. All of the above can be filled with exactly the same loop:

    for muon in muons:
        histogram.fill(muon)

## Benefits

Moving specialized logic out of the for loop allows the physicist to describe an entire analysis declaratively, in subdirectories like:

    Label(dir1 = Label(hist1 = Bin(...),
                       hist2 = Bin(...)),
          dir2 = ...)

This tree expresses what the physicist wants to aggregate and how they want to cut it up. The actual aggregation can be performed by an automated system that handles indexing, parallelization, and merging partial results. Each primitive has a custom `fill` method and a `+` operator for (mutable) aggregating and (immutable) merging, which fits perfectly into Spark's `aggregate` functional, Hadoop's `reduce`, an SQL aggregation function, parallel processing on GPUs, etc. It makes analysis code independent of the system where data are analyzed.

In addition, it formalizes the analysis so that it can be inspected algorithmically. At any level, the cuts applied to a particular histogram can be inferred by tracing the primitives from the root of the tree to that histogram. Named functions provide bookkeeping, so that a quantity and its label are defined in one place, so units can be correctly changed across a suite of plots with one change.

## Scope

Histogrammar aggregates data but does not produce plots (much like HBOOK, which had an associated HPLOT for 1970's line printers). Histogrammar has extensions to pass its aggregated data to many different plotting libraries. A user can aggregate data in a hard-to-reach place, such as an intermediate value in a GPU calculation or a remote supercomputer, bring back the aggregated histogram as JSON, and plot it in their favorite package. Aggregation and plotting are separate, so that changing the color of axis tickmarks doesn't require re-running the analysis code.

## Status and Documentation

### For developers

[See the wiki](../../wiki) for communication among the Histogrammar developers, which includes fine-grained status updates and future goals. [Travis-CI](http://travis-ci.org/diana-hep/histogrammar) shows the current state of the build and unit tests.

### For users

The average user should go to [histogrammar.org/docs](http://histogrammar.org/docs) for tutorials, examples, and reference documentation.

Developers and users should both report errors on the [issues tab](http://github.com/diana-hep/histogrammar/issues).

### Installation

Histogrammar will someday be available in popular repositories, such as Maven Central (for Java/Scala), PyPI (for Python), CRAN (for R), npm (for Javascript), etc. For now, use the [releases tab](http://github.com/diana-hep/histogrammar/releases) to get a fixed release or clone this repository to get the bleeding edge.

[Installation instructions](http://histogrammar.org/docs/install.html) are available on the user documentation site.

### The last word

Histogrammar is in an experimental state, with some features working and some basic features still in development. It is not analysis-ready, but will become so faster if users try it out on their pet projects and provide feedback. Please let us know if anything doesn't work or doesn't make sense!
