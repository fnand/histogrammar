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

#include "histogrammar.h"

std::unique_ptr<Counted> Count::ed(double entries) { return std::unique_ptr<Counted>(new Counted(entries)); }

template <typename DATUM>
std::unique_ptr<Counting<DATUM> > Count::ing() { return std::unique_ptr<Counting<DATUM> >(new Counting<DATUM>()); }

Counted::Counted(double entries) : entries_(entries) { }

double Counted::entries() { return entries_; }

std::unique_ptr<Counted> Counted::zero() { return std::unique_ptr<Counted>(new Counted(0.0)); }

std::unique_ptr<Counted> Counted::plus(std::unique_ptr<Counted> &that) { return std::unique_ptr<Counted>(new Counted(this->entries() + that->entries())); }

template <typename DATUM>
Counting<DATUM>::Counting() : Counted(0.0) { }

template <typename DATUM>
void Counting<DATUM>::fill(DATUM datum, double weight) {
  entries_ += weight;
}


