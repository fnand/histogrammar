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

#include "json.hpp"

using json = nlohmann::json;

namespace histogrammar {
  //////////////////////////////////////////////////////////////// utilities

  std::string VERSION = "0.5-prerelease";

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
    virtual double entries() const = 0;
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
    double entries_;
    Counted(double entries) : entries_(entries) { }

  public:
    using factory_type = Count;
    Counted(const Counted &that) : entries_(that.entries()) { }
    const std::string name() const { return factory_type::name(); }

    double entries() const { return entries_; }

    const Counted zero() const { return Counted(0.0); }
    const Counted operator+(const Counted &that) const { return Counted(entries() + that.entries()); }
    const bool operator==(const Counted &that) const { return entries() == that.entries(); }

    const json toJsonFragment() const {
      return entries();
    }
  };

  template <typename DATUM>
  class Counting : public Container<Counting<DATUM> >, public Aggregation<DATUM> {
    friend class Count;
  private:
    double entries_;
    Counting(double entries) : entries_(entries) { }

  public:
    using factory_type = Count;
    Counting(const Counting<DATUM> &that) : entries_(that.entries()) { }
    const std::string name() const { return factory_type::name(); }

    double entries() const { return entries_; }

    const Counting<DATUM> zero() const { return Counting<DATUM>(0.0); }
    const Counting<DATUM> operator+(const Counting<DATUM> &that) const { return Counting<DATUM>(entries() + that.entries()); }
    const bool operator==(const Counting &that) const { return entries() == that.entries(); }

    void fill(DATUM datum, double weight = 1.0) {
      entries_ += weight;
    }

    const json toJsonFragment() const {
      return entries();
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
    template <typename DATUM> static const ing_type<DATUM> ing(std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection = makeUnweighted<DATUM>());

    static const ed_type fromJsonFragment(const json &j);
    static const ed_type fromJson(const json &j);
  };

  class Summed : public Container<Summed> {
    friend class Sum;
  private:
    double entries_;
    double sum_;
    Summed(double entries, double sum) : entries_(entries), sum_(sum) { }

  public:
    using factory_type = Sum;
    Summed(const Summed &that) : entries_(that.entries()), sum_(that.sum()) { }
    const std::string name() const { return factory_type::name(); }

    double entries() const { return entries_; }
    double sum() const { return sum_; }

    const Summed zero() const { return Summed(0.0, 0.0); }
    const Summed operator+(const Summed &that) const { return Summed(entries() + that.entries(), sum() + that.sum()); }
    const bool operator==(const Summed &that) const { return entries() == that.entries()  &&  sum() == that.sum(); }

    const json toJsonFragment() const {
      return {
        {"entries", entries()},
        {"sum", sum()},
      };
    }
  };

  template <typename DATUM> class Summing : public Container<Summing<DATUM> >, public Aggregation<DATUM> {
    friend class Sum;
  private:
    double entries_;
    double sum_;
    Summing(std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection, double entries, double sum) : quantity(quantity), selection(selection), entries_(entries), sum_(sum) { }

  public:
    using factory_type = Sum;
    Summing(const Summing &that) : quantity(that.quantity), selection(that.selection), entries_(that.entries()), sum_(that.sum()) { }
    const std::function<double(DATUM)> quantity;
    const std::function<double(DATUM)> selection;
    const std::string name() const { return factory_type::name(); }

    double entries() const { return entries_; }
    double sum() const { return sum_; }

    const Summing<DATUM> zero() const { return Summing<DATUM>(quantity, selection, 0.0, 0.0); }
    const Summing<DATUM> operator+(const Summing<DATUM> &that) const { return Summing<DATUM>(quantity, selection, entries() + that.entries(), sum() + that.sum()); }
    const bool operator==(const Summing &that) const { return entries() == that.entries()  &&  sum() == that.sum(); }

    void fill(DATUM datum, double weight = 1.0) {
      double w = weight * selection(datum);
      if (w > 0.0) {
        double q = quantity(datum);
        entries_ += w;
        sum_ += q * w;
      }
    }

    const json toJsonFragment() const {
      return {
        {"entries", entries()},
        {"sum", sum()},
      };
    }
  };

  const Summed Sum::ed(double entries, double sum) { return Summed(entries, sum); }

  template <typename DATUM> const Summing<DATUM> Sum::ing(std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection) { return Summing<DATUM>(quantity, selection, 0.0, 0.0); }

  const Summed Sum::fromJsonFragment(const json &j) {
    return Summed(j["entries"].get<double>(), j["sum"].get<double>());
  }

  const Summed Sum::fromJson(const json &j) {
    assert(j["type"].get<std::string>() == Sum::name());
    return Sum::fromJsonFragment(j["data"]);
  }

  //////////////////////////////////////////////////////////////// Bin/Binned/Binning

  template <typename V> class Binned;
  template <typename DATUM, typename V> class Binning;

  class Bin : public Factory {
  public:
    template <typename V> using ed_type = Binned<V>;
    template <typename DATUM, typename V> using ing_type = Binning<DATUM, V>;

    static const std::string name() { return "Bin"; }

    template <typename V> static const ed_type<V> ed(double low, double high, double entries, std::vector<V> values);
    template <typename DATUM, typename V> static const ing_type<DATUM, V> ing(int num, double low, double high, std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection = makeUnweighted<DATUM>(), V value = Count::ing<DATUM>());

    template <typename V> static const ed_type<V> fromJsonFragment(const json &j);
    template <typename V> static const ed_type<V> fromJson(const json &j);
  };

  class BinMethods {
  public:
    virtual int num() const = 0;
    virtual double low() const = 0;
    virtual double high() const = 0;

    int bin(double x) const {
      if (under(x)  ||  over(x)  ||  nan(x))
        return -1;
      else
        return (int)floor(num() * (x - low()) / (high() - low()));
    }

    bool under(double x) const {
      return !isnan(x)  &&  x < low();
    }
    bool over(double x) const {
      return !isnan(x)  &&  x >= high();
    }
    bool nan(double x) const {
      return isnan(x);
    }

    const std::vector<int> indexes() const {
      std::vector<int> out(num());
      std::iota(out.begin(), out.end(), 0);
      return out;
    }
    const std::pair<double, double> range(int index) const {
      return std::pair<double, double>((high() - low()) * index / num() + low(), (high() - low()) * (index + 1) / num() + low());
    }
  };

  template <typename V> class Binned : public Container<Binned<V> >, public BinMethods {
    friend class Bin;
  protected:
    double low_;
    double high_;
    double entries_;
    std::vector<V> values_;
    Binned(double low, double high, double entries, std::vector<V> values) : low_(low), high_(high), entries_(entries), values_(values) {
      static_assert(std::is_base_of<Container<V>, V>::value, "Binned values type must be a Container");
      if (low >= high)
        throw std::invalid_argument(std::string("low (") + std::to_string(low) + std::string(") must be less than high (") + std::to_string(high) + std::string(")"));
      if (values.size() < 1)
        throw std::invalid_argument(std::string("values must have at least one element"));
      if (entries < 0.0)
        throw std::invalid_argument(std::string("entries (") + std::to_string(entries) + std::string(") cannot be negative"));
    }
  public:
    using factory_type = Bin;
    Binned(const Binned &that) : low_(that.low()), high_(that.high()), entries_(that.entries()), values_(that.values()) { }

    const std::string name() const { return factory_type::name(); }

    int num() const { return values_.size(); }
    double low() const { return low_; }
    double high() const { return high_; }
    double entries() const { return entries_; }
    const std::vector<V> &values() const { return values_; }

    const V &at(int index) const { return values_[index]; }

    const Binned<V> zero() const {
      std::vector<V> newvalues;
      newvalues.reserve(num());
      for (int i = 0;  i < num();  i++)
        newvalues.push_back(at(i).zero());
      return Binned<V>(low_, high_, entries_, newvalues);
    }

    const Binned<V> operator+(const Binned<V> &that) const {
      if (low() != that.low())
        throw std::invalid_argument(std::string("cannot add Binned because low differs (") + std::to_string(low()) + std::string(" vs ") + std::to_string(that.low()) + std::string(")"));
      if (high() != that.high())
        throw std::invalid_argument(std::string("cannot add Binned because high differs (") + std::to_string(high()) + std::string(" vs ") + std::to_string(that.high()) + std::string(")"));
      if (num() != that.num())
        throw std::invalid_argument(std::string("cannot add Binned because number of values differs (") + std::to_string(num()) + std::string(" vs ") + std::to_string(that.num()) + std::string(")"));

      std::vector<V> newvalues;
      newvalues.reserve(num());
      for (int i = 0;  i < num();  i++)
        newvalues.push_back(at(i) + that.at(i));
      return Binned<V>(low_, high_, entries_, newvalues);
    }

    const bool operator==(const Binned<V> &that) const {
      return low() == that.low()  &&  high() == that.high()  &&  entries() == that.entries()  &&  values() == that.values();
    }

    const json toJsonFragment() const {
      std::vector<json> newvalues;
      newvalues.reserve(num());
      for (int i = 0;  i < num();  i++)
        newvalues.push_back(at(i).toJsonFragment());
      return {
        {"low", low()},
        {"high", high()},
        {"entries", entries()},
        {"values:type", values_[0].name()},
        {"values", newvalues},
      };
    }
  };

  template <typename DATUM, typename V> class Binning : public Container<Binning<DATUM, V> >, public Aggregation<DATUM>, public BinMethods {
    friend class Bin;
  private:
    double low_;
    double high_;
    double entries_;
    std::vector<V> values_;
    Binning(double low, double high, std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection, double entries, std::vector<V> values) : low_(low), high_(high), quantity(quantity), selection(selection), entries_(entries), values_(values) {
      static_assert(std::is_base_of<Container<V>, V>::value, "Binning values type must be a Container");
      static_assert(std::is_base_of<Aggregation<DATUM>, V>::value, "Binning values type must have Aggregation for this data type");
      if (low >= high)
        throw std::invalid_argument(std::string("low (") + std::to_string(low) + std::string(") must be less than high (") + std::to_string(high) + std::string(")"));
      if (values.size() < 1)
        throw std::invalid_argument(std::string("values must have at least one element"));
      if (entries < 0.0)
        throw std::invalid_argument(std::string("entries (") + std::to_string(entries) + std::string(") cannot be negative"));
    }
  public:
    using factory_type = Bin;
    Binning(const Binning &that) : low_(that.low()), high_(that.high()), quantity(quantity), selection(selection), entries_(that.entries()), values_(that.values()) { }
    const std::string name() const { return factory_type::name(); }

    const std::function<double(DATUM)> quantity;
    const std::function<double(DATUM)> selection;

    int num() const { return values_.size(); }
    double low() const { return low_; }
    double high() const { return high_; }
    double entries() const { return entries_; }
    const std::vector<V> values() const { return values_; }

    const V &at(int index) const { return values_[index]; }

    const Binning<DATUM, V> zero() const {
      std::vector<V> newvalues;
      newvalues.reserve(num());
      for (int i = 0;  i < num();  i++)
        newvalues.push_back(at(i).zero());
      return Binning<DATUM, V>(low_, high_, quantity, selection, entries_, newvalues);
    }

    const Binning<DATUM, V> operator+(const Binning<DATUM, V> &that) const {
      if (low() != that.low())
        throw std::invalid_argument(std::string("cannot add Binned because low differs (") + std::to_string(low()) + std::string(" vs ") + std::to_string(that.low()) + std::string(")"));
      if (high() != that.high())
        throw std::invalid_argument(std::string("cannot add Binned because high differs (") + std::to_string(high()) + std::string(" vs ") + std::to_string(that.high()) + std::string(")"));
      if (num() != that.num())
        throw std::invalid_argument(std::string("cannot add Binned because number of values differs (") + std::to_string(num()) + std::string(" vs ") + std::to_string(that.num()) + std::string(")"));

      std::vector<V> newvalues;
      newvalues.reserve(num());
      for (int i = 0;  i < num();  i++)
        newvalues.push_back(at(i) + that.at(i));
      return Binning<DATUM, V>(low_, high_, quantity, selection, entries_, newvalues);
    }

    const bool operator==(const Binning<DATUM, V> &that) const {
      return low() == that.low()  &&  high() == that.high()  &&  entries() == that.entries()  &&  values() == that.values();
    }

    void fill(DATUM datum, double weight = 1.0) {
      double w = weight * selection(datum);
      if (w > 0.0) {
        double q = quantity(datum);

        entries_ += w;
        if (under(q))
          nullptr;
        else if (over(q))
          nullptr;
        else if (nan(q))
          nullptr;
        else
          values_[bin(q)].fill(datum, w);
      }
    }

    const json toJsonFragment() const {
      std::vector<json> newvalues;
      newvalues.reserve(num());
      for (int i = 0;  i < num();  i++)
        newvalues.push_back(at(i).toJsonFragment());
      return {
        {"low", low()},
        {"high", high()},
        {"entries", entries()},
        {"values:type", values_[0].name()},
        {"values", newvalues},
      };
    }
  };

  template <typename V> const Binned<V> Bin::ed(double low, double high, double entries, std::vector<V> values) {
    return Binned<V>(low, high, entries, values);
  }

  template <typename DATUM, typename V> const Binning<DATUM, V> Bin::ing(int num, double low, double high, std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection, const V value) {
    std::vector<V> values;
    values.reserve(num);
    for (int i = 0;  i < num;  i++)
      values.push_back(value.zero());
    return Binning<DATUM, V>(low, high, quantity, selection, 0.0, values);
  }

  template <typename V> const Binned<V> Bin::fromJsonFragment(const json &j) {
    json jv = j["values"];
    std::vector<V> values;
    values.reserve(jv.size());
    for (int i = 0;  i < jv.size();  i++)
      values.push_back(V::factory_type::fromJsonFragment(jv[i]));

    for (int i = 0;  i < jv.size();  i++)
      assert(j["values:type"] == values[i].name());

    return Binned<V>(j["low"].get<double>(), j["high"].get<double>(), j["entries"].get<double>(), values);
  }

  template <typename V> const Binned<V> Bin::fromJson(const json &j) {
    assert(j["type"].get<std::string>() == Bin::name());
    return Bin::fromJsonFragment<V>(j["data"]);
  }
}

#endif // HISTOGRAMMAR_HPP
