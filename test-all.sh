#!/bin/sh

set -e

echo
echo "##################################################################"
echo "## test C++                                                     ##"
echo "##################################################################"
echo
cd cpp
make
cd ..

echo
echo "##################################################################"
echo "## test Python2 and Python3                                     ##"
echo "##################################################################"
echo
cd python
python setup.py test
python3 setup.py test
cd ..

echo
echo "##################################################################"
echo "## test Scala                                                   ##"
echo "##################################################################"
echo
cd scala
mvn test
cd ..
cd scala-sparksql
mvn test
cd ..
# cd scala-bokeh
# mvn test
# cd ..
