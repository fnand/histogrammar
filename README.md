# Histogrammar

Histogrammar is a declarative grammar for booking, filling, and merging histograms. It simplifies data reduction, especially in functional, distributed environments like Apache Spark. An entire analysis can be wrapped up as a single "meta-histogram" to be processed by workers running in parallel and then combined into a coherent result.

That was the formal explanation. Here's a typical use-case:




Histogram abstraction to coordinate the filling of hundreds of histograms in a data analysis.

  * [Scaladocs for the Scala version](http://diana-hep.org/histogrammar/scala/0.1/index.html#org.dianahep.histogrammar.package)
