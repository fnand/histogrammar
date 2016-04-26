all:
	g++ src/defs.cpp -Iinclude -o libhistogrammar.so -fPIC -shared
	g++ test/test.cpp -Iinclude -L. -lhistogrammar -o test_histogrammar
	./test_histogrammar

clean:
	rm libhistogrammar.so test_histogrammar
