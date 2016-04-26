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

#ifndef HISTOGRAMMAR_H
#define HISTOGRAMMAR_H

#include <string>
#include <memory>

class Factory {
public:
  virtual std::string name() = 0;
  virtual int fromJsonFragment() = 0;   // FIXME
  static int fromJson();                // FIXME
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

class Counted;
template <typename DATUM> class Counting;

class Count {
public:
  static std::unique_ptr<Counted> ed(double entries);
  template <typename DATUM>
  static std::unique_ptr<Counting<DATUM> > ing();
};

class Counted : public Container<Counted> {
  friend class Count;
  template <typename DATUM> friend class Counting;
private:
  Counted(double entries_);
protected:
  double entries_;
public:
  double entries();
  std::unique_ptr<Counted> zero();
  std::unique_ptr<Counted> plus(std::unique_ptr<Counted> &that);
};

template <typename DATUM>
class Counting : public Counted, Aggregation<DATUM> {
  friend class Count;
private:
  Counting();
public:
  void fill(DATUM datum, double weight = 1.0);
};



// class Binning<DATUM> : public Container<CONTAINER>, Aggregation<DATUM> {
// public:

// };


#endif // HISTOGRAMMAR_H
