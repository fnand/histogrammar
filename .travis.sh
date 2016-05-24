#!/bin/sh

set -e

################################## test C++
CXX="g++-4.8"
cd cpp
make
cd ..

################################## test Python2 and Python3
cd python
python setup.py test
python3 setup.py test
cd ..

################################## test Scala
cd scala
mvn test
cd ..
