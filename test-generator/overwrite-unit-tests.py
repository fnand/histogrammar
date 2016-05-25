#!/usr/bin/env python

simple = [3.4, 2.2, -1.8, 0.0, 7.3, -4.7, 1.6, 0.0, -3.0, -1.7]

class Struct(object):
    def __init__(self, x, y, z, w):
        self.bool = x
        self.int = y
        self.double = z
        self.string = w
    def __repr__(self):
        return "Struct({}, {}, {}, {})".format(self.bool, self.int, self.double, repr(self.string))

struct = [Struct(True,  -2,  3.4, "one"),
          Struct(False, -1,  2.2, "two"),
          Struct(True,   0, -1.8, "three"),
          Struct(False,  1,  0.0, "four"),
          Struct(False,  2,  7.3, "five"),
          Struct(False,  3, -4.7, "six"),
          Struct(True,   4,  1.6, "seven"),
          Struct(True,   5,  0.0, "eight"),
          Struct(False,  6, -3.0, "nine"),
          Struct(True,   7, -1.7, "ten")]

def mean(x):
    if len(x) == 0:
        return 0.0
    else:
        return sum(x) / len(x)

def meanWeighted(x, w):
    if not any(_ > 0.0 for _ in w):
        return 0.0
    else:
        return sum(xi * max(wi, 0.0) for xi, wi in zip(x, w)) / sum(_ for _ in w if _ > 0.0)

def variance(x):
    if len(x) == 0:
        return 0.0
    else:
        return sum(math.pow(_, 2) for _ in x) / len(x) - math.pow(sum(x) / len(x), 2)

def varianceWeighted(x, w):
    if not any(_ > 0.0 for _ in w):
        return 0.0
    else:
        return sum(xi**2 * max(wi, 0.0) for xi, wi in zip(x, w)) / sum(_ for _ in w if _ > 0.0) - math.pow(sum(xi * max(wi, 0.0) for xi, wi in zip(x, w)) / sum(_ for _ in w if _ > 0.0), 2)

def mae(x):
    if len(x) == 0:
        return 0.0
    else:
        return sum(map(abs, x)) / len(x)

def maeWeighted(x, w):
    if not any(_ > 0.0 for _ in w):
        return 0.0
    else:
        return sum(abs(xi) * max(wi, 0.0) for xi, wi in zip(x, w)) / sum(_ > 0.0 for _ in w)

class PurePython(object):
    var = "x"

    def __init__(self, name, constructor, autoassertions=()):
        self.name = name
        self.constructor = constructor
        self.autoassertions = autoassertions
        self.tests = []

    def test(self, fillvalue, assertion):
        for x in self.autoassertions:
            self.tests.append(x)
        if fillvalue is not None:
            self.tests.append("{}.fill({})".format(self.var, fillvalue))
        self.tests.append(assertion)

    def string(self):
        out = []
        out.append("    def test_{}(self):".format(self.name))
        out.append("        {} = {}".format(self.var, self.constructor))
        for test in self.tests:
            out.append("        " + test)
        return "\n".join(out)

if __name__ == "__main__":
    ################################################ <init>

    purePython = []
    purePythonAutoAssertions = [
        "self.assertEqual({}, {})".format(PurePython.var, PurePython.var),
        "self.assertEqual(ed({}), ed({}))".format(PurePython.var, PurePython.var),
        "self.assertEqual(hash({}), hash({}))".format(PurePython.var, PurePython.var),
        "self.assertEqual(hash(ed({})), hash(ed({})))".format(PurePython.var, PurePython.var),
        "self.assertEqual({}, {} + {}.zero())".format(PurePython.var, PurePython.var, PurePython.var),
        "self.assertEqual(ed({}), ed({}) + ed({}).zero())".format(PurePython.var, PurePython.var, PurePython.var),
        "self.assertEqual(ed({} + {}), ed({}) + ed({}))".format(PurePython.var, PurePython.var, PurePython.var, PurePython.var),
        "self.assertEqual({}, pickle.loads(pickle.dumps({})))".format(PurePython.var, PurePython.var),
        "self.assertEqual(ed({}), pickle.loads(pickle.dumps(ed({}))))".format(PurePython.var, PurePython.var),
        "self.assertEqual(ed({}), ed(pickle.loads(pickle.dumps({}))))".format(PurePython.var, PurePython.var),
        ]

    ################################################ Count

    count         = PurePython("count",         "Count()", purePythonAutoAssertions)
    weightedCount = PurePython("weightedCount", "Count()", purePythonAutoAssertions)

    for i in range(10):
        count.test(simple[i], "self.assertAlmostEqualJSON({}.toJson(), {})".format(PurePython.var, {"type": "Count", "data": i + 1.0}))
        weightedCount.test("3.14, {}".format(simple[i]), "self.assertAlmostEqualJSON({}.toJson(), {})".format(PurePython.var, {"type": "Count", "data": sum(filter(lambda _: _ >= 0.0, simple[:i+1]))}))

    purePython.append(count)
    purePython.append(weightedCount)

    ################################################ Sum

    summ            = PurePython("summ",            "Sum(lambda x: x + 1)",                     purePythonAutoAssertions)
    sumWithName     = PurePython("sumWithName",     "Sum(named('something', lambda x: x + 1))", purePythonAutoAssertions)
    sumString       = PurePython("sumString",     "Sum('x + 1')",                             purePythonAutoAssertions)
    sumStruct       = PurePython("sumStruct",       "Sum(lambda x: x.double + 1)",              purePythonAutoAssertions)
    sumStructString = PurePython("sumStructString", "Sum('double + 1')",                        purePythonAutoAssertions)

    for i in range(10):
        result = sum(simple[:i+1]) + i + 1.0
        summ.test(           simple[i], "self.assertAlmostEqualJSON({}.toJson(), {})".format(PurePython.var, {"type": "Sum", "data": {"entries": i + 1.0, "sum": result}}))
        sumWithName.test(    simple[i], "self.assertAlmostEqualJSON({}.toJson(), {})".format(PurePython.var, {"type": "Sum", "data": {"entries": i + 1.0, "sum": result, "name": "something"}}))
        sumString.test(      simple[i], "self.assertAlmostEqualJSON({}.toJson(), {})".format(PurePython.var, {"type": "Sum", "data": {"entries": i + 1.0, "sum": result, "name": "x + 1"}}))
        sumStruct.test(      struct[i], "self.assertAlmostEqualJSON({}.toJson(), {})".format(PurePython.var, {"type": "Sum", "data": {"entries": i + 1.0, "sum": result}}))
        sumStructString.test(struct[i], "self.assertAlmostEqualJSON({}.toJson(), {})".format(PurePython.var, {"type": "Sum", "data": {"entries": i + 1.0, "sum": result, "name": "double + 1"}}))

    purePython.append(summ)
    purePython.append(sumWithName)
    purePython.append(sumString)
    purePython.append(sumStruct)
    purePython.append(sumStructString)








    ################################################ <finalize>

    template = open("TEMPLATES/PurePython.py").read()
    open("../python/test/autogenerated.py", "w").write(template.replace("{{TESTS}}", "\n\n".join(p.string() for p in purePython)))
