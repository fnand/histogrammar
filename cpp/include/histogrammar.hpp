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

#ifndef HISTOGRAMMAR_HPP
#define HISTOGRAMMAR_HPP

#include <algorithm>
#include <assert.h>
#include <math.h>
#include <stdexcept>
#include <string>
#include <vector>
#include <numeric>
#include "json.hpp"

using json = nlohmann::json;

namespace histogrammar {
  //////////////////////////////////////////////////////////////// utilities

  std::string VERSION = "0.7-prerelease";

  template <typename DATUM> std::function<double(DATUM)> makeUnweighted() {
    return [](DATUM datum){return 1.0;};
  }

  //////////////////////////////////////////////////////////////// general definition of an container and mix-in

  template <typename CONTAINER> class Container;

  class Factory {
    static const std::string name();
  };

  template <typename CONTAINER> class Container {
  public:
    virtual const std::string name() const = 0;
    virtual const CONTAINER zero() const = 0;
    virtual const CONTAINER operator+(const CONTAINER &that) const = 0;
    virtual const bool operator==(const CONTAINER &that) const = 0;
    virtual const json toJsonFragment() const = 0;
    const json toJson() const {
      return {
        {"type", name()},
        {"data", toJsonFragment()},
      };
    }
  };

  template <typename DATUM> class Aggregation {
  public:
    virtual void fill(DATUM datum, double weight = 1.0) = 0;
  };

  //////////////////////////////////////////////////////////////// Count/Counted/Counting

  class Counted;
  template <typename DATUM> class Counting;

  class Count : public Factory {
  public:
    using ed_type = Counted;
    template <typename DATUM> using ing_type = Counting<DATUM>;
    static const std::string name() { return "Count"; }

    static const Counted ed(double entries);
    template <typename DATUM> static const Counting<DATUM> ing();

    static const Counted fromJsonFragment(const json &j);
    static const Counted fromJson(const json &j);
  };

  class Counted : public Container<Counted> {
    friend class Count;
  private:
    Counted(double entries) : entries(entries) {
      if (entries < 0.0)
        throw std::invalid_argument(std::string("entries (") + std::to_string(entries) + std::string(") cannot be negative"));
    }

  public:
    using factory_type = Count;
    Counted(const Counted &that) : entries(that.entries) { }
    const std::string name() const { return factory_type::name(); }

    const double entries;

    const Counted zero() const { return Counted(0.0); }
    const Counted operator+(const Counted &that) const { return Counted(entries + that.entries); }
    const bool operator==(const Counted &that) const { return entries == that.entries; }

    const json toJsonFragment() const {
      return entries;
    }
  };

  template <typename DATUM>
  class Counting : public Container<Counting<DATUM> >, public Aggregation<DATUM> {
    friend class Count;
  private:
    Counting(double entries) : entries(entries) { }

  public:
    using factory_type = Count;
    Counting(const Counting<DATUM> &that) : entries(that.entries) { }
    const std::string name() const { return factory_type::name(); }

    double entries;

    const Counting<DATUM> zero() const { return Counting<DATUM>(0.0); }
    const Counting<DATUM> operator+(const Counting<DATUM> &that) const { return Counting<DATUM>(entries + that.entries); }
    const bool operator==(const Counting &that) const { return entries == that.entries; }

    void fill(DATUM datum, double weight = 1.0) {
      // no possibility of exception from here on out (for rollback)
      entries += weight;
    }

    const json toJsonFragment() const {
      return entries;
    }
  };

  const Counted Count::ed(double entries) { return Counted(entries); }

  template <typename DATUM> const Counting<DATUM> Count::ing() { return Counting<DATUM>(0.0); }

  const Counted Count::fromJsonFragment(const json &j) {
    return Counted(j.get<double>());
  }

  const Counted Count::fromJson(const json &j) {
    assert(j["type"].get<std::string>() == Count::name());
    return Count::fromJsonFragment(j["data"]);
  }

  //////////////////////////////////////////////////////////////// Sum/Summed/Summing

  class Summed;
  template <typename DATUM> class Summing;

  class Sum : public Factory {
  public:
    using ed_type = Summed;
    template <typename DATUM> using ing_type = Summing<DATUM>;
    static const std::string name() { return "Sum"; }

    static const ed_type ed(double entries, double sum);
    template <typename DATUM> static const ing_type<DATUM> ing(std::function<double(DATUM)> quantity);

    static const ed_type fromJsonFragment(const json &j);
    static const ed_type fromJson(const json &j);
  };

  class Summed : public Container<Summed> {
    friend class Sum;
  private:
    Summed(double entries, double sum) : entries(entries), sum(sum) {
      if (entries < 0.0)
        throw std::invalid_argument(std::string("entries (") + std::to_string(entries) + std::string(") cannot be negative"));
    }

  public:
    using factory_type = Sum;
    Summed(const Summed &that) : entries(that.entries), sum(that.sum) { }
    const std::string name() const { return factory_type::name(); }

    const double entries;
    const double sum;

    const Summed zero() const { return Summed(0.0, 0.0); }
    const Summed operator+(const Summed &that) const { return Summed(entries + that.entries, sum + that.sum); }
    const bool operator==(const Summed &that) const { return entries == that.entries  &&  sum == that.sum; }

    const json toJsonFragment() const {
      return {
        {"entries", entries},
        {"sum", sum},
      };
    }
  };

  template <typename DATUM> class Summing : public Container<Summing<DATUM> >, public Aggregation<DATUM> {
    friend class Sum;
  private:
    Summing(std::function<double(DATUM)> quantity, double entries, double sum) : quantity(quantity), entries(entries), sum(sum) { }

  public:
    using factory_type = Sum;
    Summing(const Summing &that) : quantity(that.quantity), entries(that.entries), sum(that.sum) { }
    const std::string name() const { return factory_type::name(); }

    const std::function<double(DATUM)> quantity;
    double entries;
    double sum;

    const Summing<DATUM> zero() const { return Summing<DATUM>(quantity, 0.0, 0.0); }
    const Summing<DATUM> operator+(const Summing<DATUM> &that) const { return Summing<DATUM>(quantity, entries + that.entries, sum + that.sum); }
    const bool operator==(const Summing &that) const { return entries == that.entries  &&  sum == that.sum; }

    void fill(DATUM datum, double weight = 1.0) {
      if (weight > 0.0) {
        double q = quantity(datum);

        // no possibility of exception from here on out (for rollback)
        entries += weight;
        sum += q * weight;
      }
    }

    const json toJsonFragment() const {
      return {
        {"entries", entries},
        {"sum", sum},
      };
    }
  };

  const Summed Sum::ed(double entries, double sum) { return Summed(entries, sum); }

  template <typename DATUM> const Summing<DATUM> Sum::ing(std::function<double(DATUM)> quantity) { return Summing<DATUM>(quantity, 0.0, 0.0); }

  const Summed Sum::fromJsonFragment(const json &j) {
    return Summed(j["entries"].get<double>(), j["sum"].get<double>());
  }

  const Summed Sum::fromJson(const json &j) {
    assert(j["type"].get<std::string>() == Sum::name());
    return Sum::fromJsonFragment(j["data"]);
  }

  //////////////////////////////////////////////////////////////// Bin/Binned/Binning

  template <typename V, typename U, typename O, typename N> class Binned;
  template <typename DATUM, typename V, typename U, typename O, typename N> class Binning;

  class Bin : public Factory {
  public:
    template <typename V, typename U, typename O, typename N> using ed_type = Binned<V, U, O, N>;
    template <typename DATUM, typename V, typename U, typename O, typename N> using ing_type = Binning<DATUM, V, U, O, N>;

    static const std::string name() { return "Bin"; }

    template <typename V, typename U, typename O, typename N> static const ed_type<V, U, O, N> ed(double low, double high, double entries, std::vector<V> values, U underflow, O overflow, N nanflow);
    template <typename DATUM, typename V, typename U, typename O, typename N> static const ing_type<DATUM, V, U, O, N> ing(int num, double low, double high, std::function<double(DATUM)> quantity, V value = Count::ing<DATUM>(), U underflow = Count::ing<DATUM>(), O overflow = Count::ing<DATUM>(), N nanflow = Count::ing<DATUM>());

    template <typename V, typename U, typename O, typename N> static const ed_type<V, U, O, N> fromJsonFragment(const json &j);
    template <typename V, typename U, typename O, typename N> static const ed_type<V, U, O, N> fromJson(const json &j);
  };

  class BinMethods {
  public:
    BinMethods(int num, double low, double high) : num(num), low(low), high(high) { }

    const int num;
    const double low;
    const double high;

    int bin(double x) const {
      if (under(x)  ||  over(x)  ||  nan(x))
        return -1;
      else
        return (int)floor(num * (x - low) / (high - low));
    }

    bool under(double x) const {
      return !isnan(x)  &&  x < low;
    }
    bool over(double x) const {
      return !isnan(x)  &&  x >= high;
    }
    bool nan(double x) const {
      return isnan(x);
    }

    const std::vector<int> indexes() const {
      std::vector<int> out(num);
      std::iota(out.begin(), out.end(), 0);
      return out;
    }
    const std::pair<double, double> range(int index) const {
      return std::pair<double, double>((high - low) * index / num + low, (high - low) * (index + 1) / num + low);
    }
  };

  template <typename V, typename U, typename O, typename N> class Binned : public Container<Binned<V, U, O, N> >, public BinMethods {
    friend class Bin;
  protected:
    Binned(double low, double high, double entries, std::vector<V> values, U underflow, O overflow, N nanflow) : BinMethods(values.size(), low, high), entries(entries), values(values), underflow(underflow), overflow(overflow), nanflow(nanflow) {
      static_assert(std::is_base_of<Container<V>, V>::value, "Binned values type must be a Container");
      static_assert(std::is_base_of<Container<U>, U>::value, "Binned underflow type must be a Container");
      static_assert(std::is_base_of<Container<O>, O>::value, "Binned overflow type must be a Container");
      static_assert(std::is_base_of<Container<N>, N>::value, "Binned nanflow type must be a Container");
      if (low >= high)
        throw std::invalid_argument(std::string("low (") + std::to_string(low) + std::string(") must be less than high (") + std::to_string(high) + std::string(")"));
      if (values.size() < 1)
        throw std::invalid_argument(std::string("values must have at least one element"));
      if (entries < 0.0)
        throw std::invalid_argument(std::string("entries (") + std::to_string(entries) + std::string(") cannot be negative"));
    }
  public:
    using factory_type = Bin;
    Binned(const Binned &that) : BinMethods(that.values.size(), that.low, that.high), entries(that.entries), values(that.values), underflow(that.underflow), overflow(that.overflow), nanflow(that.nanflow) { }
    const std::string name() const { return factory_type::name(); }

    const double entries;
    const std::vector<V> values;
    const U underflow;
    const O overflow;
    const N nanflow;

    const V &at(int index) const { return values[index]; }

    const Binned<V, U, O, N> zero() const {
      std::vector<V> newvalues;
      newvalues.reserve(num);
      for (int i = 0;  i < num;  i++)
        newvalues.push_back(at(i).zero());
      return Binned<V, U, O, N>(low, high, entries, newvalues, underflow.zero(), overflow.zero(), nanflow.zero());
    }

    const Binned<V, U, O, N> operator+(const Binned<V, U, O, N> &that) const {
      if (low != that.low)
        throw std::invalid_argument(std::string("cannot add Binned because low differs (") + std::to_string(low) + std::string(" vs ") + std::to_string(that.low) + std::string(")"));
      if (high != that.high)
        throw std::invalid_argument(std::string("cannot add Binned because high differs (") + std::to_string(high) + std::string(" vs ") + std::to_string(that.high) + std::string(")"));
      if (num != that.num)
        throw std::invalid_argument(std::string("cannot add Binned because number of values differs (") + std::to_string(num) + std::string(" vs ") + std::to_string(that.num) + std::string(")"));

      std::vector<V> newvalues;
      newvalues.reserve(num);
      for (int i = 0;  i < num;  i++)
        newvalues.push_back(at(i) + that.at(i));
      return Binned<V, U, O, N>(low, high, entries, newvalues, underflow + that.underflow, overflow + that.overflow, nanflow + that.nanflow);
    }

    const bool operator==(const Binned<V, U, O, N> &that) const {
      return low == that.low  &&  high == that.high  &&  entries == that.entries  &&  values == that.values  &&  underflow == that.underflow  &&  overflow == that.overflow  &&  nanflow == that.nanflow;
    }

    const json toJsonFragment() const {
      std::vector<json> newvalues;
      newvalues.reserve(num);
      for (int i = 0;  i < num;  i++)
        newvalues.push_back(at(i).toJsonFragment());
      return {
        {"low", low},
        {"high", high},
        {"entries", entries},
        {"values:type", values[0].name()},
        {"values", newvalues},
        {"underflow:type", underflow.name()},
        {"underflow", underflow.toJsonFragment()},
        {"overflow:type", overflow.name()},
        {"overflow", overflow.toJsonFragment()},
        {"nanflow:type", nanflow.name()},
        {"nanflow", nanflow.toJsonFragment()},
      };
    }
  };

  template <typename DATUM, typename V, typename U, typename O, typename N> class Binning : public Container<Binning<DATUM, V, U, O, N> >, public Aggregation<DATUM>, public BinMethods {
    friend class Bin;
  private:
    Binning(double low, double high, std::function<double(DATUM)> quantity, double entries, std::vector<V> values, U underflow, O overflow, N nanflow) : BinMethods(values.size(), low, high), quantity(quantity), entries(entries), values(values), underflow(underflow), overflow(overflow), nanflow(nanflow) {

      static_assert(std::is_base_of<Container<V>, V>::value, "Binning values type must be a Container");
      static_assert(std::is_base_of<Aggregation<DATUM>, V>::value, "Binning values type must have Aggregation for this data type");
      static_assert(std::is_base_of<Container<U>, U>::value, "Binning underflow type must be a Container");
      static_assert(std::is_base_of<Aggregation<DATUM>, U>::value, "Binning underflow type must have Aggregation for this data type");
      static_assert(std::is_base_of<Container<O>, O>::value, "Binning overflow type must be a Container");
      static_assert(std::is_base_of<Aggregation<DATUM>, O>::value, "Binning overflow type must have Aggregation for this data type");
      static_assert(std::is_base_of<Container<N>, N>::value, "Binning nanflow type must be a Container");
      static_assert(std::is_base_of<Aggregation<DATUM>, N>::value, "Binning nanflow type must have Aggregation for this data type");

      if (low >= high)
        throw std::invalid_argument(std::string("low (") + std::to_string(low) + std::string(") must be less than high (") + std::to_string(high) + std::string(")"));
      if (values.size() < 1)
        throw std::invalid_argument(std::string("values must have at least one element"));
      if (entries < 0.0)
        throw std::invalid_argument(std::string("entries (") + std::to_string(entries) + std::string(") cannot be negative"));
    }

  public:
    using factory_type = Bin;
    Binning(const Binning &that) : BinMethods(that.values.size(), that.low, that.high), quantity(quantity), entries(that.entries), values(that.values), underflow(that.underflow), overflow(that.overflow), nanflow(that.nanflow) { }
    const std::string name() const { return factory_type::name(); }

    const std::function<double(DATUM)> quantity;
    double entries;
    std::vector<V> values;
    U underflow;
    O overflow;
    N nanflow;

    const V &at(int index) const { return values[index]; }

    const Binning<DATUM, V, U, O, N> zero() const {
      std::vector<V> newvalues;
      newvalues.reserve(num);
      for (int i = 0;  i < num;  i++)
        newvalues.push_back(at(i).zero());
      return Binning<DATUM, V, U, O, N>(low, high, quantity, entries, newvalues, underflow, overflow, nanflow);
    }

    const Binning<DATUM, V, U, O, N> operator+(const Binning<DATUM, V, U, O, N> &that) const {
      if (low != that.low)
        throw std::invalid_argument(std::string("cannot add Binned because low differs (") + std::to_string(low) + std::string(" vs ") + std::to_string(that.low) + std::string(")"));
      if (high != that.high)
        throw std::invalid_argument(std::string("cannot add Binned because high differs (") + std::to_string(high) + std::string(" vs ") + std::to_string(that.high) + std::string(")"));
      if (num != that.num)
        throw std::invalid_argument(std::string("cannot add Binned because number of values differs (") + std::to_string(num) + std::string(" vs ") + std::to_string(that.num) + std::string(")"));

      std::vector<V> newvalues;
      newvalues.reserve(num);
      for (int i = 0;  i < num;  i++)
        newvalues.push_back(at(i) + that.at(i));
      return Binning<DATUM, V, U, O, N>(low, high, quantity, entries, newvalues, underflow + that.underflow, overflow + that.overflow, nanflow + that.nanflow);
    }

    const bool operator==(const Binning<DATUM, V, U, O, N> &that) const {
      return low == that.low  &&  high == that.high  &&  entries == that.entries  &&  values == that.values  &&  underflow == that.underflow  &&  overflow == that.overflow  &&  nanflow == that.nanflow;
    }

    void fill(DATUM datum, double weight = 1.0) {
      if (weight > 0.0) {
        double q = quantity(datum);

        if (under(q))
          underflow.fill(datum, weight);
        else if (over(q))
          overflow.fill(datum, weight);
        else if (nan(q))
          nanflow.fill(datum, weight);
        else
          values[bin(q)].fill(datum, weight);

        // no possibility of exception from here on out (for rollback)
        entries += weight;
      }
    }

    const json toJsonFragment() const {
      std::vector<json> newvalues;
      newvalues.reserve(num);
      for (int i = 0;  i < num;  i++)
        newvalues.push_back(at(i).toJsonFragment());
      return {
        {"low", low},
        {"high", high},
        {"entries", entries},
        {"values:type", values[0].name()},
        {"values", newvalues},
        {"underflow:type", underflow.name()},
        {"underflow", underflow.toJsonFragment()},
        {"overflow:type", overflow.name()},
        {"overflow", overflow.toJsonFragment()},
        {"nanflow:type", nanflow.name()},
        {"nanflow", nanflow.toJsonFragment()},
      };
    }
  };

  template <typename V, typename U, typename O, typename N> const Binned<V, U, O, N> Bin::ed(double low, double high, double entries, std::vector<V> values, U underflow, O overflow, N nanflow) {
    return Binned<V, U, O, N>(low, high, entries, values, underflow, overflow, nanflow);
  }

  template <typename DATUM, typename V, typename U, typename O, typename N> const Binning<DATUM, V, U, O, N> Bin::ing(int num, double low, double high, std::function<double(DATUM)> quantity, const V value, const U underflow, const O overflow, const N nanflow) {
    std::vector<V> values;
    values.reserve(num);
    for (int i = 0;  i < num;  i++)
      values.push_back(value.zero());
    return Binning<DATUM, V, U, O, N>(low, high, quantity, 0.0, values, underflow.zero(), overflow.zero(), nanflow.zero());
  }

  template <typename V, typename U, typename O, typename N> const Binned<V, U, O, N> Bin::fromJsonFragment(const json &j) {
    json jv = j["values"];
    std::vector<V> values;
    values.reserve(jv.size());
    for (int i = 0;  i < jv.size();  i++)
      values.push_back(V::factory_type::fromJsonFragment(jv[i]));

    for (int i = 0;  i < jv.size();  i++)
      assert(j["values:type"] == values[i].name());

    U underflow = U::factory_type::fromJsonFragment(j["underflow"]);
    assert(j["underflow:type"] == underflow.name());

    U overflow = U::factory_type::fromJsonFragment(j["overflow"]);
    assert(j["overflow:type"] == overflow.name());

    U nanflow = U::factory_type::fromJsonFragment(j["nanflow"]);
    assert(j["nanflow:type"] == nanflow.name());

    return Binned<V, U, O, N>(j["low"].get<double>(), j["high"].get<double>(), j["entries"].get<double>(), values, underflow, overflow, nanflow);
  }

  template <typename V, typename U, typename O, typename N> const Binned<V, U, O, N> Bin::fromJson(const json &j) {
    assert(j["type"].get<std::string>() == Bin::name());
    return Bin::fromJsonFragment<V, U, O, N>(j["data"]);
  }

  //////////////////////////////////////////////////////////////// Cut/Cutted/Cutting

  template <typename V> class Cutted;
  template <typename DATUM, typename V> class Cutting;

  class Cut : public Factory {
  public:
    template <typename V> using ed_type = Cutted<V>;
    template <typename DATUM, typename V> using ing_type = Cutting<DATUM, V>;
    static const std::string name() { return "Cut"; }

    template <typename V> static const ed_type<V> ed(double entries, V value);
    template <typename DATUM, typename V> static const ing_type<DATUM, V> ing(std::function<double(DATUM)> selection, V value);

    template <typename V> static const ed_type<V> fromJsonFragment(const json &j);
    template <typename V> static const ed_type<V> fromJson(const json &j);
  };

  template <typename V> class Cutted : public Container<Cutted<V> > {
    friend class Cut;
  private:
    Cutted(double entries, V value) : entries(entries), value(value) {
      static_assert(std::is_base_of<Container<V>, V>::value, "Cutted values type must be a Container");
      if (entries < 0.0)
        throw std::invalid_argument(std::string("entries (") + std::to_string(entries) + std::string(") cannot be negative"));
    }

  public:
    using factory_type = Cut;
    Cutted(const Cutted &that) : entries(that.entries), value(that.value) { }
    const std::string name() const { return factory_type::name(); }

    const double entries;
    const V value;

    double fractionPassing() { return value.entries / entries; }

    const Cutted<V> zero() const { return Cutted<V>(0.0, value.zero()); }
    const Cutted<V> operator+(const Cutted<V> &that) const { return Cutted<V>(entries + that.entries, value + that.value); }
    const bool operator==(const Cutted<V> &that) const { return entries == that.entries  &&  value == that.value; }

    const json toJsonFragment() const {
      return {
        {"entries", entries},
        {"type", value.name()},
        {"data", value.toJsonFragment()},
      };
    }
  };

  template <typename DATUM, typename V> class Cutting : public Container<Cutting<DATUM, V> >, public Aggregation<DATUM> {
    friend class Cut;
  private:
    Cutting(double entries, std::function<double(DATUM)> selection, V value) : entries(entries), selection(selection), value(value) {
      static_assert(std::is_base_of<Container<V>, V>::value, "Cutting values type must be a Container");
      static_assert(std::is_base_of<Aggregation<DATUM>, V>::value, "Cutting values type must have Aggregation for this data type");
    }
  public:
    using factory_type = Cut;
    Cutting(const Cutting &that) : entries(that.entries), selection(selection), value(that.value) { }
    const std::string name() const { return factory_type::name(); }

    double entries;
    const std::function<double(DATUM)> selection;
    V value;

    double fractionPassing() { return value.entries / entries; }

    const Cutting<DATUM, V> zero() const { return Cutting<DATUM, V>(0.0, selection, value.zero()); }
    const Cutting<DATUM, V> operator+(const Cutting<DATUM, V> &that) const { return Cutting<DATUM, V>(entries + that.entries, selection, value + that.value); }
    const bool operator==(const Cutting<DATUM, V> &that) const { return entries == that.entries  &&  value == that.value; }

    void fill(DATUM datum, double weight = 1.0) {
      double w = weight * selection(datum);
      if (w > 0.0)
        value.fill(datum, w);

      // no possibility of exception from here on out (for rollback)
      entries += weight;
    }

    const json toJsonFragment() const {
      return {
        {"entries", entries},
        {"type", value.name()},
        {"data", value.toJsonFragment()},
      };
    }
  };

  template <typename V> const Cutted<V> Cut::ed(double entries, V value) { return Cutted<V>(entries, value); }

  template <typename DATUM, typename V> const Cutting<DATUM, V> Cut::ing(std::function<double(DATUM)> selection, V value) {
    return Cutting<DATUM, V>(0.0, selection, value);
  }

  template <typename V> const Cutted<V> Cut::fromJsonFragment(const json &j) {
    V value = V::factory_type::fromJsonFragment(j["data"]);
    assert(j["type"].get<std::string>() == value.name());
    return Cutted<V>(j["entries"].get<double>(), value);
  }

  template <typename V> const Cutted<V> Cut::fromJson(const json &j) {
    assert(j["type"].get<std::string>() == Cut::name());
    return Cut::fromJsonFragment<V>(j["data"]);
  }

}

#endif // HISTOGRAMMAR_HPP
