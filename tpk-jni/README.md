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

