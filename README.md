# Histogrammar

Histogrammar is a declarative grammar for booking histograms with automatic filling and merging. It simplifies the process of reducing huge, distributed datasets to a form that can be plotted, especially in a functional workflow like Apache Spark.

Here's a simple example:

```scala
import org.dianahep.histogrammar._
import org.dianahep.histogrammar.histogram._

val px_histogram = Histogram(100, -5, 5, {mu: Muon => mu.px})
val pt_histogram = Histogram(80, 0, 8, {mu: Muon => Math.sqrt(mu.px*mu.px + mu.py*mu.py)})
val cut_histogram = Histogram(100, -5, 5, {mu: Muon => mu.px}, {mu: Muon => mu.py < 0.0})

val all_histograms = Label("px" -> px_histogram, "pt" -> pt_histogram, "cut" -> cut_histogram)

val final_histogram = rdd.aggregate(all_histograms)(new Increment, new Combine)
```

where `rdd` is a Spark RDD of `Muon` objects. The histograms carry their own fill rules, so when they're embedded in a "meta-histogram" called `Label` (with three "bins": `"px"`, `"pt"`, and `"cut"`), they each know how to fill themselves. That way, the `new Increment` function, which adds data to copies of the histograms on all of Spark's worker nodes, and the `new Combine` function, which merges the partial histograms into a final result, can be generated without any additional code.

Alternatively, one would have to manage the same list of histograms in three functions: booking, incrementing, and combining, which is more open to mistakes.








```scala
println(final_histogram("pt").ascii)
```
```
                        0                                                   6657.20
                        +---------------------------------------------------------+
underflow          0    |                                                         |
[  0    ,  0.100)  513  |****                                                     |
[  0.100,  0.200)  1487 |*************                                            |
[  0.200,  0.300)  2508 |*********************                                    |
[  0.300,  0.400)  3303 |****************************                             |
[  0.400,  0.5  )  4084 |***********************************                      |
[  0.5  ,  0.600)  4740 |*****************************************                |
[  0.600,  0.700)  5325 |**********************************************           |
[  0.700,  0.800)  5597 |************************************************         |
[  0.800,  0.900)  6011 |***************************************************      |
[  0.900,  1    )  6004 |***************************************************      |
[  1    ,  1.10 )  6052 |****************************************************     |
[  1.10 ,  1.20 )  5780 |*************************************************        |
[  1.20 ,  1.30 )  5646 |************************************************         |
[  1.30 ,  1.40 )  5400 |**********************************************           |
[  1.40 ,  1.5  )  5100 |********************************************             |
[  1.5  ,  1.60 )  4688 |****************************************                 |
[  1.60 ,  1.70 )  4276 |*************************************                    |
[  1.70 ,  1.80 )  3770 |********************************                         |
[  1.80 ,  1.90 )  3334 |*****************************                            |
[  1.90 ,  2    )  2912 |*************************                                |
[  2    ,  2.10 )  2480 |*********************                                    |
[  2.10 ,  2.20 )  2052 |******************                                       |
[  2.20 ,  2.30 )  1842 |****************                                         |
[  2.30 ,  2.40 )  1511 |*************                                            |
[  2.40 ,  2.5  )  1234 |***********                                              |
[  2.5  ,  2.60 )  974  |********                                                 |
[  2.60 ,  2.70 )  808  |*******                                                  |
[  2.70 ,  2.80 )  550  |*****                                                    |
[  2.80 ,  2.90 )  505  |****                                                     |
[  2.90 ,  3    )  382  |***                                                      |
...
```

Histogram abstraction to coordinate the filling of hundreds of histograms in a data analysis.

  * [Scaladocs for the Scala version](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.package)