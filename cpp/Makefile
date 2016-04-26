all:
	g++ src/defs.cpp -std=c++11 -Iinclude -o libhistogrammar.so -fPIC -shared
	g++ test/test.cpp -std=c++11 -Iinclude -L. -lhistogrammar -o test_histogrammar
	./test_histogrammar

clean:
	rm libhistogrammar.so test_histogrammar
