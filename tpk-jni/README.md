# tpk-jni: Native Code used by TCS pk Assembly

This directory contains native C++ code that provides a shared library that is loaded at runtime by the TCS pk assembly.
It depends on the [CSW C API](https://github.com/tmtsoftware/csw-c) for publishing events 
as well shared libraries from the (TMT private repo) TPK. 
used for calculating mount and enclosure positions.

## Building

The Makefile provided here forwards commands to the cmake build.

Targets:

* make all - to compile (in ./build)
* make clean - to remove generated files (./build)
* make install - installs the libs and .h files (in /usr/local by default)
* make test - run tests

## Running

This library is loaded automatically at runtime by Scala code.
Note that by setting the environment variable TPK_USE_FAKE_SYSTEM_CLOCK
you can force the internal clock to start at MDJ = midnight, making tests
more reproducible.
