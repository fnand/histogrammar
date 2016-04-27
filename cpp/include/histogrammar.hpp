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

#include <string>
#include <memory>

#include "json.hpp"

using json = nlohmann::json;

namespace histogrammar {
  //////////////////////////////////////////////////////////////// utilities

  template <typename DATUM>
  std::function<double(DATUM)> makeUnweighted() {
    return [](DATUM datum){return 1.0;};
  }

  //////////////////////////////////////////////////////////////// general definition of an container, its factory, and mix-in

  class Factory {
  public:
    virtual std::string name() = 0;
    // virtual int fromJsonFragment() = 0;   // FIXME
    // static int fromJson();                // FIXME
  };

  template <typename CONTAINER>
  class Container {
  public:
    virtual double entries() = 0;
    virtual std::unique_ptr<CONTAINER> zero() = 0;
    virtual std::unique_ptr<CONTAINER> plus(std::unique_ptr<CONTAINER> &that) = 0;
  };

  template <typename DATUM>
  class Aggregation {
  public:
    virtual void fill(DATUM datum, double weight = 1.0) = 0;
  };

  //////////////////////////////////////////////////////////////// Count/Counted/Counting

  class Counted;
  template <typename DATUM> class Counting;

  class Count : public Factory {
  public:
    std::string name() { return "Count"; }
    static std::unique_ptr<Counted> ed(double entries);
    template <typename DATUM>
    static std::unique_ptr<Counting<DATUM> > ing();
  };

  class Counted : public Container<Counted> {
    friend class Count;
  protected:
    Counted(double entries) : entries_(entries) { }
    double entries_;
  public:
    double entries() { return entries_; }
    std::unique_ptr<Counted> zero() { return std::unique_ptr<Counted>(new Counted(0.0)); }
    std::unique_ptr<Counted> plus(std::unique_ptr<Counted> &that) {
      return std::unique_ptr<Counted>(new Counted(this->entries() + that->entries()));
    }
  };

  template <typename DATUM>
  class Counting : public Counted, Aggregation<DATUM> {
    friend class Count;
  private:
    Counting() : Counted(0.0) { }
  public:
    void fill(DATUM datum, double weight = 1.0) {
      entries_ += weight;
    }
  };

  std::unique_ptr<Counted> Count::ed(double entries) { return std::unique_ptr<Counted>(new Counted(entries)); }

  template <typename DATUM>
  std::unique_ptr<Counting<DATUM> > Count::ing() { return std::unique_ptr<Counting<DATUM> >(new Counting<DATUM>()); }

  //////////////////////////////////////////////////////////////// Sum/Summed/Summing

  class Summed;
  template <typename DATUM> class Summing;

  class Sum : public Factory {
  public:
    std::string name() { return "Sum"; }
    static std::unique_ptr<Summed> ed(double entries, double sum);
    template <typename DATUM>
    static std::unique_ptr<Summing<DATUM> > ing(std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection = makeUnweighted<DATUM>());
  };

  class Summed : public Container<Summed> {
    friend class Sum;
  protected:
    Summed(double entries, double sum) : entries_(entries), sum_(sum) { }
    double entries_;
    double sum_;
  public:
    double entries() { return entries_; }
    double sum() { return sum_; }
    std::unique_ptr<Summed> zero() { return std::unique_ptr<Summed>(new Summed(0.0, 0.0)); }
    std::unique_ptr<Summed> plus(std::unique_ptr<Summed> &that) {
      return std::unique_ptr<Summed>(new Summed(this->entries() + that->entries(), this->sum() + that->sum()));
    }
  };

  template <typename DATUM>
  class Summing : public Summed, Aggregation<DATUM> {
    friend class Sum;
  private:
    Summing(std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection) : Summed(0.0, 0.0), quantity(quantity), selection(selection) { }
  public:
    const std::function<double(DATUM)> quantity;
    const std::function<double(DATUM)> selection;
    void fill(DATUM datum, double weight = 1.0) {
      double w = weight * selection(datum);
      if (w > 0.0) {
        double q = quantity(datum);
        entries_ += w;
        sum_ += q * w;
      }
    }
  };

  std::unique_ptr<Summed> Sum::ed(double entries, double sum) { return std::unique_ptr<Summed>(new Summed(entries, sum)); }

  template <typename DATUM>
  std::unique_ptr<Summing<DATUM> > Sum::ing(std::function<double(DATUM)> quantity, std::function<double(DATUM)> selection) { return std::unique_ptr<Summing<DATUM> >(new Summing<DATUM>(quantity, selection)); }

  //////////////////////////////////////////////////////////////// Bin/Binned/Binning

  // class Binning<DATUM> : public Container<CONTAINER>, Aggregation<DATUM> {
  // public:

  // };

}

#endif // HISTOGRAMMAR_HPP
