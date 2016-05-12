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

package org.dianahep

import org.dianahep.histogrammar.json._
import org.dianahep.histogrammar.util._

package histogrammar {
  //////////////////////////////////////////////////////////////// Bag/Bagged/Bagging

  /** Accumulate raw numbers, vectors of numbers, or strings, merging identical values.
    * 
    * Factory produces mutable [[org.dianahep.histogrammar.Bagging]] and immutable [[org.dianahep.histogrammar.Bagged]] objects.
    */
  object Bag extends Factory {
    val name = "Bag"
    val help = "Accumulate raw numbers, vectors of numbers, or strings, merging identical values."
    val detailedHelp = """Bag(quantity: UserFcn[DATUM, RANGE])"""

    /** Create an immutable [[org.dianahep.histogrammar.Bagged]] from arguments (instead of JSON).
      * 
      * @param entries Weighted number of entries (sum of all observed weights).
      * @param quantityName Optional name given to the quantity function, passed for bookkeeping.
      * @param values Distinct multidimensional vectors and the (weighted) number of times they were observed or `None` if they were dropped.
      */
    def ed[RANGE](entries: Double, quantityName: Option[String], values: Map[RANGE, Double]) =
      new Bagged[RANGE](entries, quantityName, values)

    /** Create an empty, mutable [[org.dianahep.histogrammar.Bagging]].
      * 
      * @param quantity Function that produces numbers, vectors of numbers, or strings.
      */
    def apply[DATUM, RANGE](quantity: UserFcn[DATUM, RANGE]) =
      new Bagging[DATUM, RANGE](quantity, 0.0, scala.collection.mutable.Map[RANGE, Double]())

    /** Synonym for `apply`. */
    def ing[DATUM, RANGE](quantity: UserFcn[DATUM, RANGE]) = apply(quantity)

    /** Use [[org.dianahep.histogrammar.Bagged]] in Scala pattern-matching. */
    def unapply[RANGE](x: Bagged[RANGE]) = x.values
    /** Use [[org.dianahep.histogrammar.Bagging]] in Scala pattern-matching. */
    def unapply[DATUM, RANGE](x: Bagging[DATUM, RANGE]) = x.values

    import KeySetComparisons._
    def fromJsonFragment(json: Json): Container[_] = json match {
      case JsonObject(pairs @ _*) if (pairs.keySet has Set("entries", "values").maybe("name")) =>
        val get = pairs.toMap

        val entries = get("entries") match {
          case JsonNumber(x) => x
          case x => throw new JsonFormatException(x, name + ".entries")
        }

        val quantityName = get.getOrElse("name", JsonNull) match {
          case JsonString(x) => Some(x)
          case JsonNull => None
          case x => throw new JsonFormatException(x, name + ".name")
        }

        val values = get("values") match {
          case JsonArray(elems @ _*) => Map[Any, Double](elems.zipWithIndex map {
            case (JsonObject(nv @ _*), i) if (nv.keySet has Set("n", "v")) =>
              val nvget = nv.toMap

              val n = nvget("n") match {
                case JsonNumber(x) => x
                case x => throw new JsonFormatException(x, name + s".values $i n")
              }

              val v: Any = nvget("v") match {
                case JsonString(x) => x
                case JsonNumber(x) => x
                case JsonArray(d @ _*) => Vector(d.zipWithIndex map {
                  case (JsonNumber(x), j) => x
                  case (x, j) => throw new JsonFormatException(x, name + s".values $i v $j")
                }: _*)
                case x => throw new JsonFormatException(x, name + s".values $i v")
              }

              (v -> n)

            case (x, i) => throw new JsonFormatException(x, name + s".values $i")
          }: _*)
          case x => throw new JsonFormatException(x, name + ".values")
        }

        new Bagged[Any](entries, quantityName, values)

      case _ => throw new JsonFormatException(json, name)
    }

    private[histogrammar] def rangeOrdering[RANGE] = new Ordering[RANGE] {
      def compare(x: RANGE, y: RANGE) = (x, y) match {
        case (xx: String, yy: String) => xx compare yy
        case (xx: Double, yy: Double) => xx compare yy
        case (xx: Vector[_], yy: Vector[_]) if (!xx.isEmpty  &&  !yy.isEmpty  &&  xx.head == yy.head) => compare(xx.tail.asInstanceOf[RANGE], yy.tail.asInstanceOf[RANGE])
        case (xx: Vector[_], yy: Vector[_]) if (!xx.isEmpty  &&  !yy.isEmpty) => xx.head.asInstanceOf[Double] compare yy.head.asInstanceOf[Double]
        case (xx: Vector[_], yy: Vector[_]) if (xx.isEmpty  &&  yy.isEmpty) => 0
        case (xx: Vector[_], yy: Vector[_]) if (xx.isEmpty) => -1
        case (xx: Vector[_], yy: Vector[_]) if (yy.isEmpty) => 1
      }
    }
  }

  /** An accumulated bag of numbers, vectors of numbers, or strings.
    * 
    * Use the factory [[org.dianahep.histogrammar.Bag]] to construct an instance.
    * 
    * @param entries Weighted number of entries (sum of all observed weights).
    * @param quantityName Optional name given to the quantity function, passed for bookkeeping.
    * @param values Distinct values and the (weighted) number of times they were observed.
    */
  class Bagged[RANGE] private[histogrammar](val entries: Double, val quantityName: Option[String], val values: Map[RANGE, Double]) extends Container[Bagged[RANGE]] {
    type Type = Bagged[RANGE]
    def factory = Bag

    def zero = new Bagged(0.0, this.quantityName, Map[RANGE, Double]())
    def +(that: Bagged[RANGE]) =
      if (this.quantityName != that.quantityName)
        throw new ContainerException(s"cannot add ${getClass.getName} because quantityName differs (${this.quantityName} vs ${that.quantityName})")
      else {
        val newentries = this.entries + that.entries
        val newvalues = {
          val out = scala.collection.mutable.Map(this.values.toSeq: _*)
          that.values foreach {case (k, v) =>
            if (out contains k)
              out(k) += v
            else
              out(k) = v
          }
          out.toMap
        }

        new Bagged[RANGE](newentries, this.quantityName, newvalues)
      }

    def toJsonFragment = {
      implicit val rangeOrdering = Bag.rangeOrdering[RANGE]
      JsonObject(
        "entries" -> JsonFloat(entries),
        "values" -> JsonArray(values.toSeq.sortBy(_._1).map({
          case (v: String, n) => JsonObject("n" -> JsonFloat(n), "v" -> JsonString(v))
          case (v: Double, n) => JsonObject("n" -> JsonFloat(n), "v" -> JsonFloat(v))
          case (v: Vector[_], n) => JsonObject("n" -> JsonFloat(n), "v" -> JsonArray(v.map({case vi: Double => JsonFloat(vi)}): _*))
        }): _*)).maybe(JsonString("name") -> quantityName.map(JsonString(_)))
    }

    override def toString() = s"""Bagged[${if (values.isEmpty) "size=0" else values.head.toString + "..., size=" + values.size.toString}]"""
    override def equals(that: Any) = that match {
      case that: Bagged[RANGE] => this.entries === that.entries  &&  this.quantityName == that.quantityName  &&  this.values == that.values
      case _ => false
    }
    override def hashCode() = (entries, quantityName, values).hashCode()
  }

  /** An accumulated bag of numbers, vectors of numbers, or strings.
    * 
    * Use the factory [[org.dianahep.histogrammar.Bag]] to construct an instance.
    * 
    * @param quantity Function that produces numbers, vectors of numbers, or strings.
    * @param entries Weighted number of entries (sum of all observed weights).
    * @param values Distinct values and the (weighted) number of times they were observed.
    */
  class Bagging[DATUM, RANGE] private[histogrammar](val quantity: UserFcn[DATUM, RANGE], var entries: Double, var values: scala.collection.mutable.Map[RANGE, Double]) extends Container[Bagging[DATUM, RANGE]] with AggregationOnData {
    type Type = Bagging[DATUM, RANGE]
    type Datum = DATUM
    def factory = Bag

    def zero = new Bagging[DATUM, RANGE](quantity, 0.0, scala.collection.mutable.Map[RANGE, Double]())
    def +(that: Bagging[DATUM, RANGE]) =
      if (this.quantity.name != that.quantity.name)
        throw new ContainerException(s"cannot add ${getClass.getName} because quantity name differs (${this.quantity.name} vs ${that.quantity.name})")
      else {
        val newentries = this.entries + that.entries
        val newvalues = {
          val out = scala.collection.mutable.Map(this.values.toSeq: _*)
          that.values foreach {case (k, v) =>
            if (out contains k)
              out(k) += v
            else
              out(k) = v
          }
          out
        }

        new Bagging[DATUM, RANGE](quantity, newentries, newvalues)
      }

    def fill[SUB <: Datum](datum: SUB, weight: Double = 1.0) {
      if (weight > 0.0) {
        val q = quantity(datum)

        // no possibility of exception from here on out (for rollback)
        entries += weight
        if (values contains q)
          values(q) += weight
        else
          values(q) = weight
      }
    }

    def toJsonFragment = {
      implicit val rangeOrdering = Bag.rangeOrdering[RANGE]
      JsonObject(
        "entries" -> JsonFloat(entries),
        "values" -> JsonArray(values.toSeq.sortBy(_._1).map({
          case (v: String, n) => JsonObject("n" -> JsonFloat(n), "v" -> JsonString(v))
          case (v: Double, n) => JsonObject("n" -> JsonFloat(n), "v" -> JsonFloat(v))
          case (v: Vector[_], n) => JsonObject("n" -> JsonFloat(n), "v" -> JsonArray(v.map({case vi: Double => JsonFloat(vi)}): _*))
        }): _*)).maybe(JsonString("name") -> quantity.name.map(JsonString(_)))
    }

    override def toString() = s"""Bagging[${if (values.isEmpty) "size=0" else values.head.toString + "..., size=" + values.size.toString}]"""
    override def equals(that: Any) = that match {
      case that: Bagging[DATUM, RANGE] => this.quantity == that.quantity  &&  this.entries === that.entries  &&  this.values == that.values
      case _ => false
    }
    override def hashCode() = (quantity, entries, values).hashCode()
  }

}
