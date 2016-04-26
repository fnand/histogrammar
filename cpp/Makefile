all:
	g++ src/defs.cpp -o libhistogrammar.so -fPIC -shared
	g++ test/test.cpp -L. -lhistogrammar -o test_histogrammar
	./test_histogrammar

clean:
	rm libhistogrammar.so test_histogrammar
