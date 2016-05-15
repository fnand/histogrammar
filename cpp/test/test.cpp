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

#include <iostream>
#include <string>

#include "histogrammar.hpp"

using namespace histogrammar;

void test_Counted() {
  auto one = Count::ed(1);
  auto two = Count::ed(2);
  std::cout << "Counted: " << one.entries << " + " << two.entries << " = " << (one + two).entries << std::endl;
  auto zero = one.zero();
  assert(Count::fromJson(one.toJson()) == one);
}

void test_Counting() {
  auto one = Count::ing<std::string>();
  auto two = Count::ing<std::string>();
  one.fill("hello");
  two.fill("hey");
  two.fill("there");
  std::cout << "Counting: " << one.entries << " + " << two.entries << " = " << (one + two).entries << std::endl;
  auto zero = one.zero();
  assert(Count::fromJson(Count::fromJson(one.toJson()).toJson()) == Count::fromJson(one.toJson()));
}

void test_CuttingCounting() {
  auto one = Cut::ing<double, Counting<double> >([](double x){return x > 3.14;}, Count::ing<double>());
  auto two = Cut::ing<double, Counting<double> >([](double x){return x > 3.14;}, Count::ing<double>());
  one.fill(3.0);
  one.fill(4.0);
  two.fill(3.0);
  two.fill(4.0);
  two.fill(5.0);
  std::cout << "CuttingCounting: " << one.value.entries << " / " << one.entries << " = " << one.fractionPassing() << std::endl;
  std::cout << "                 " << two.value.entries << " / " << two.entries << " = " << two.fractionPassing() << std::endl;
  auto three = one + two;
  std::cout << "                 " << three.value.entries << " / " << three.entries << " = " << three.fractionPassing() << std::endl;
  auto zero = one.zero();
  assert(Cut::fromJson<Counted>(Cut::fromJson<Counted>(one.toJson()).toJson()) == Cut::fromJson<Counted>(one.toJson()));
}

void test_Summed() {
  auto one = Sum::ed(1, 1);
  auto two = Sum::ed(2, 2);
  std::cout << "Summed entries: " << one.entries << " + " << two.entries << " = " << (one + two).entries << std::endl;
  std::cout << "Summed sum: " << one.sum << " + " << two.sum << " = " << (one + two).sum << std::endl;
  auto zero = one.zero();
  assert(Sum::fromJson(one.toJson()) == one);
}

void test_Summing() {
  auto one = Sum::ing<std::string>([](std::string datum){return (double)datum.size();});
  auto two = Sum::ing<std::string>([](std::string datum){return (double)datum.size();});
  one.fill("hello");
  two.fill("hey");
  two.fill("there");
  std::cout << "Summing entries: " << one.entries << " + " << two.entries << " = " << (one + two).entries << std::endl;
  std::cout << "Summing sum: " << one.sum << " + " << two.sum << " = " << (one + two).sum << std::endl;
  auto zero = one.zero();
  assert(Sum::fromJson(Sum::fromJson(one.toJson()).toJson()) == Sum::fromJson(one.toJson()));
}

void test_Binned() {
  const std::vector<Counted> onevalues = {Count::ed(1), Count::ed(2), Count::ed(3)};
  const std::vector<Counted> twovalues = {Count::ed(3), Count::ed(2), Count::ed(1)};

  auto one = Bin::ed(-3, 5, 0.0, onevalues, Count::ed(0), Count::ed(0), Count::ed(0));
  auto two = Bin::ed(-3, 5, 0.0, twovalues, Count::ed(0), Count::ed(0), Count::ed(0));
  auto three = one + two;

  std::cout << "Binned values: " << three.at(0).entries << " " << three.at(1).entries << " " << three.at(2).entries << std::endl;
  auto zero = one.zero();
  assert((Bin::fromJson<Counted, Counted, Counted, Counted>(one.toJson()) == one));
}

void test_Binning() {
  auto one = Bin::ing<std::string, Counting<std::string>, Counting<std::string>, Counting<std::string>, Counting<std::string> >(5, 0.5, 5.5, [](std::string datum){return (double)datum.size();});
  auto two = Bin::ing<std::string, Counting<std::string>, Counting<std::string>, Counting<std::string>, Counting<std::string> >(5, 0.5, 5.5, [](std::string datum){return (double)datum.size();});

  one.fill("hello");
  two.fill("hey");
  two.fill("there");

  auto three = one + two;

  std::cout << "Binning values: " << three.at(0).entries << " " << three.at(1).entries << " " << three.at(2).entries << " " << three.at(3).entries << " " << three.at(4).entries << std::endl;
  auto zero = one.zero();
  assert((Bin::fromJson<Counted, Counted, Counted, Counted>(Bin::fromJson<Counted, Counted, Counted, Counted>(one.toJson()).toJson()) == Bin::fromJson<Counted, Counted, Counted, Counted>(one.toJson())));
}

int main(int argc, char **argv) {
  std::cout << "Version " << VERSION << std::endl;

  test_Counted();
  test_Counting();
  test_CuttingCounting();

  test_Summed();
  test_Summing();

  test_Binned();
  test_Binning();

  return 0;
}
