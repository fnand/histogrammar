#!/usr/bin/env python

# Copyright 2016 Jim Pivarski
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import math
import pickle
import unittest

from histogrammar import *
from histogrammar.histogram import Histogram

class Struct(object):
    def __init__(self, x, y, z, w):
        self.bool = x
        self.int = y
        self.double = z
        self.string = w
    def __repr__(self):
        return "Struct({}, {}, {}, {})".format(self.bool, self.int, self.double, repr(self.string))

def ed(x):
    return Factory.fromJson(x.toJson())

class TestEverything(unittest.TestCase):
    def assertAlmostEqualJSON(self, x, y):
        if isinstance(x, dict) and isinstance(y, dict):
            if set(x.keys()) == set(y.keys()):
                for k in x.keys():
                    self.assertAlmostEqualJSON(x[k], y[k])
            else:
                raise AssertionError("keys {} are not equal to keys {}".format(sorted(x.keys()), sorted(y.keys())))

        elif isinstance(x, list) and isinstance(y, list):
            if len(x) == len(y):
                for xi, yi in zip(x, y):
                    self.assertAlmostEqualJSON(xi, yi)
            else:
                raise AssertionError("length of {} is not equal to length of {}".format(x, y))

        elif isinstance(x, basestring) and isinstance(y, basestring):
            self.assertEqual(x, y)

        elif isinstance(x, (int, long, float)) and isinstance(y, (int, long, float)):
            self.assertAlmostEqual(x, y)

        else:
            self.assertEqual(x, y)

{{TESTS}}
