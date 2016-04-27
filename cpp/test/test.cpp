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
  std::cout << "Counted: " << one->entries() << " + " << two->entries() << " = " << one->plus(two)->entries() << std::endl;
}

void test_Counting() {
  auto one = Count::ing<std::string>();
  auto two = Count::ing<std::string>();
  one->fill("hello");
  two->fill("hey");
  two->fill("there");

  auto oneed = std::unique_ptr<Counted>(std::move(one));
  auto twoed = std::unique_ptr<Counted>(std::move(two));

  std::cout << "Counting: " << oneed->entries() << " + " << twoed->entries() << " = " << oneed->plus(twoed)->entries() << std::endl;
}

void test_Summed() {
  auto one = Sum::ed(1, 1);
  auto two = Sum::ed(2, 2);
  std::cout << "Summed entries: " << one->entries() << " + " << two->entries() << " = " << one->plus(two)->entries() << std::endl;
  std::cout << "Summed sum: " << one->sum() << " + " << two->sum() << " = " << one->plus(two)->sum() << std::endl;
}

void test_Summing() {
  auto one = Sum::ing<std::string>([](std::string datum){return (double)datum.size();});
  auto two = Sum::ing<std::string>([](std::string datum){return (double)datum.size();});
  one->fill("hello");
  two->fill("hey");
  two->fill("there");

  auto oneed = std::unique_ptr<Summed>(std::move(one));
  auto twoed = std::unique_ptr<Summed>(std::move(two));

  std::cout << "Summing entries: " << oneed->entries() << " + " << twoed->entries() << " = " << oneed->plus(twoed)->entries() << std::endl;
  std::cout << "Summing sum: " << oneed->sum() << " + " << twoed->sum() << " = " << oneed->plus(twoed)->sum() << std::endl;
}
int main(int argc, char **argv) {
  test_Counted();
  test_Counting();

  test_Summed();
  test_Summing();

  return 0;
}
