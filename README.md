# TCS Vertical Slice Demo

*Note: This project is still in early development.*

This project contains demo CSW assemblies and test client code based on the TCS API
as defined the the [ICD Database](https://github.com/tmtsoftware/icd).

It makes use of the Scala based CSW framework, and also links with native C/C++ code.
For example, the pk (Pointing Kernel) assembly receives a `SlewToTarget` command and then
makes a call to C/C++ code that then posts events using the [CSW C API](https://github.com/tmtsoftware/csw-c).

At this early point, the only code subscribing to those events is in the [tcs-client](tcs-client) subproject.
It is planned to add a web app at some point to receive those events over the [ESW](https://github.com/tmtsoftware/esw) gateway
and display information, graphics or image, etc.

## Dependencies

Currently this project requires that a number of shared libraries are installed in a known location (default: /usr/local/lib),
which must also be included (on Linux) in the LD_LIBRARY_PATH environment variable.

Run `make; sudo make install` in the following projects (Note: In these projects the Makefile calls `cmake` to do the actual build).
First follow the instructions in the [csw-c README](https://github.com/tmtsoftware/csw-c) to install the required C libraries (libhiredis, libcbor, libuuid). You can also use `make PREFIX=/my/dir` to change the installation directory.

* [TPK (branch: add-cmake-files)](https://github.com/tmtsoftware/TPK/tree/add-cmake-files)

* [CSW C API (csw-c)](https://github.com/tmtsoftware/csw-c)

* [tpk-jni (a C/C++ based subproject of this project)](tpk-jni)

## Running the pk assembly

To run the pk assembly, run: 
    
    csw-services start  # Note: Make sure you are using the version for csw-4.0.0-M1 or greater

    sbt stage

    ./target/universal/stage/bin/tcs-deploy --local ./tcs-deploy/src/main/resources/PkContainer.conf

To see the events being fired from the C/C++ code, you can run the pk-event-client:

    pk-event-client



    