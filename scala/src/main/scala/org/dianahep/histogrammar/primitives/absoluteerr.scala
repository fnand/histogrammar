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
  //////////////////////////////////////////////////////////////// AbsoluteErr/AbsoluteErred/AbsoluteErring

  /** Accumulate the weighted Mean Absolute Error (MAE) of a quantity whose nominal value is zero.
    * 
    * Factory produces mutable [[org.dianahep.histogrammar.AbsoluteErring]] and immutable [[org.dianahep.histogrammar.AbsoluteErred]] objects.
    */
  object AbsoluteErr extends Factory {
    val name = "AbsoluteErr"
    val help = "Accumulate the weighted Mean Absolute Error (MAE) of a quantity whose nominal value is zero."
    val detailedHelp = """AbsoluteErr(quantity: UserFcn[DATUM, Double])"""

    /** Create an immutable [[org.dianahep.histogrammar.AbsoluteErred]] from arguments (instead of JSON).
      * 
      * @param entries Weighted number of entries (sum of all observed weights).
      * @param mae Sum of absolute differences of the quantity from zero (Mean Absolute Error).
      */
    def ed(entries: Double, mae: Double) = new AbsoluteErred(entries, None, mae)

    /** Create an empty, mutable [[org.dianahep.histogrammar.AbsoluteErring]].
      * 
      * @param quantity Numerical function to track.
      */
    def apply[DATUM](quantity: UserFcn[DATUM, Double]) = new AbsoluteErring(quantity, 0.0, 0.0)

    /** Synonym for `apply`. */
    def ing[DATUM](quantity: UserFcn[DATUM, Double]) = apply(quantity)

    /** Use [[org.dianahep.histogrammar.AbsoluteErred]] in Scala pattern-matching. */
    def unapply(x: AbsoluteErred) = Some(x.mae)
    /** Use [[org.dianahep.histogrammar.AbsoluteErring]] in Scala pattern-matching. */
    def unapply[DATUM](x: AbsoluteErring[DATUM]) = Some(x.mae)

    import KeySetComparisons._
    def fromJsonFragment(json: Json, nameFromParent: Option[String]): Container[_] with NoAggregation = json match {
      case JsonObject(pairs @ _*) if (pairs.keySet has Set("entries", "mae").maybe("name")) =>
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

        val mae = get("mae") match {
          case JsonNumber(x) => x
          case x => throw new JsonFormatException(x, name + ".mae")
        }

        new AbsoluteErred(entries, (nameFromParent ++ quantityName).lastOption, mae)

      case _ => throw new JsonFormatException(json, name)
    }

    private[histogrammar] def plus(ca: Double, ma: Double, cb: Double, mb: Double) =
      (ca + cb, (ca*ma + cb*mb)/(ca + cb))
  }

  /** An accumulated weighted Mean Absolute Error (MAE) of a quantity whose nominal value is zero.
    * 
    * Use the factory [[org.dianahep.histogrammar.AbsoluteErr]] to construct an instance.
    * 
    * @param entries Weighted number of entries (sum of all observed weights).
    * @param quantityName Optional name given to the quantity function, passed for bookkeeping.
    * @param mae Sum of absolute differences of the quantity from zero (Mean Absolute Error).
    */
  class AbsoluteErred private[histogrammar](val entries: Double, val quantityName: Option[String], val mae: Double) extends Container[AbsoluteErred] with NoAggregation with QuantityName {
    type Type = AbsoluteErred
    def factory = AbsoluteErr

    if (entries < 0.0)
      throw new ContainerException(s"entries ($entries) cannot be negative")

    def zero = new AbsoluteErred(0.0, this.quantityName, 0.0)
    def +(that: AbsoluteErred) =
      if (this.quantityName != that.quantityName)
        throw new ContainerException(s"cannot add ${getClass.getName} because quantityName differs (${this.quantityName} vs ${that.quantityName})")
      else {
        val (newentries, newmae) = AbsoluteErr.plus(this.entries, this.mae, that.entries, that.mae)
        new AbsoluteErred(newentries, this.quantityName, newmae)
      }

    def toJsonFragment(suppressName: Boolean) = JsonObject(
      "entries" -> JsonFloat(entries),
      "mae" -> JsonFloat(mae)).
      maybe(JsonString("name") -> (if (suppressName) None else quantityName.map(JsonString(_))))

    def children = Nil

    override def toString() = s"AbsoluteErred[$mae]"
    override def equals(that: Any) = that match {
      case that: AbsoluteErred => this.entries === that.entries  &&  this.quantityName == that.quantityName  &&  this.mae === that.mae
      case _ => false
    }
    override def hashCode() = (entries, quantityName, mae).hashCode
  }

  /** Accumulating a weighted Mean Absolute Error (MAE) of a quantity whose nominal value is zero.
    * 
    * Use the factory [[org.dianahep.histogrammar.AbsoluteErr]] to construct an instance.
    * 
    * @param quantity Numerical function to track.
    * @param entries Weighted number of entries (sum of all observed weights).
    * @param _mae Sum of absolute differences of the quantity from zero (Mean Absolute Error).
    */
  class AbsoluteErring[DATUM] private[histogrammar](val quantity: UserFcn[DATUM, Double], var entries: Double, _mae: Double) extends Container[AbsoluteErring[DATUM]] with AggregationOnData with NumericalQuantity[DATUM] {
    type Type = AbsoluteErring[DATUM]
    type Datum = DATUM
    def factory = AbsoluteErr

    if (entries < 0.0)
      throw new ContainerException(s"entries ($entries) cannot be negative")
    private var absoluteSum = entries * _mae

    /** sum of absolute differences of the quantity from zero (Mean Absolute Error) */
    def mae =
      if (entries == 0.0)
        _mae
      else
        absoluteSum / entries

    def mae_=(_mae: Double) {
      absoluteSum = entries * _mae
    }

    def zero = new AbsoluteErring[DATUM](quantity, 0.0, 0.0)
    def +(that: AbsoluteErring[DATUM]) =
      if (this.quantity.name != that.quantity.name)
        throw new ContainerException(s"cannot add ${getClass.getName} because quantity name differs (${this.quantity.name} vs ${that.quantity.name})")
      else {
        val (newentries, newmae) = AbsoluteErr.plus(this.entries, this.mae, that.entries, that.mae)
        new AbsoluteErring[DATUM](this.quantity, newentries, newmae)
      }

    def fill[SUB <: Datum](datum: SUB, weight: Double = 1.0) {
      if (weight > 0.0) {
        val q = quantity(datum)

        // no possibility of exception from here on out (for rollback)
        entries += weight
        absoluteSum += weight * Math.abs(q)
      }
    }

    def children = Nil

    def toJsonFragment(suppressName: Boolean) = JsonObject(
      "entries" -> JsonFloat(entries),
      "mae" -> JsonFloat(mae)).
      maybe(JsonString("name") -> (if (suppressName) None else quantity.name.map(JsonString(_))))

    override def toString() = s"AbsoluteErring[$mae]"
    override def equals(that: Any) = that match {
      case that: AbsoluteErring[DATUM] => this.quantity == that.quantity  &&  this.entries === that.entries  &&  this.mae === that.mae
      case _ => false
    }
    override def hashCode() = (quantity, entries, mae).hashCode
  }
}
