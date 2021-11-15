# TCS Vertical Slice Demo

This project contains demo CSW assemblies and test client code based on the TCS API
as defined the the [ICD Database](https://github.com/tmtsoftware/icd).

It makes use of the Scala based CSW framework, and also links with native C/C++ code
(using [JNR - Java Native Runtime](https://github.com/jnr/jnr-ffi/blob/master/docs/README.md)).
For example, the pk (Pointing Kernel) assembly receives a `SlewToTarget` command and then
makes a call to C/C++ code that calls TPK routines to set a target and 
then posts events using the [CSW C API](https://github.com/tmtsoftware/csw-c).

You can subscribe to those events for test purposes using the [tcs-client](tcs-client) command line subproject,
which just prints out the events received on stdout.

Also the [CSW Event Monitor](https://github.com/tmtsoftware/csw-event-monitor) uses
the [ESW](https://github.com/tmtsoftware/esw) gateway
to subscribe to events and can be used to display tables and charts based on the event values.

Note: In order to test this project with the CSW Event Monitor, you will currently need to run 
the [icdwebserver](https://github.com/tmtsoftware/icd) on localhost and import a slightly
modified version of the [TCS-Model-Files](https://github.com/tmt-icd/TCS-Model-Files) repository
(branch: `tcs-vslice-04-test`). The branch has been modified to use the CSW altAzCoord type instead
of separate parameters for alt and az, etc.
The assemblies provided here assumes the use of the altAzCoord type and posts events using it.

## Dependencies

Currently this project requires that a number of shared libraries are installed in a known location (default: /usr/local/lib),
which must also be included:

* On Linux: in the LD_LIBRARY_PATH environment variable
* On MacOS: in the DYLD_FALLBACK_LIBRARY_PATH environment variable

Run `make; sudo make install` in the following projects (Note: In these projects the Makefile calls `cmake` to do the actual build).
First follow the instructions in the [csw-c README](https://github.com/tmtsoftware/csw-c) to install the required C libraries (libhiredis, libcbor, libuuid). You can also use `make PREFIX=/my/dir` to change the installation directory.

* TPK (branch: add-cmake-files)]

* [CSW C API (csw-c)](https://github.com/tmtsoftware/csw-c)

* [tpk-jni (a C/C++ based subproject of this project)](tpk-jni)

## Making a release dir: install.sh

The install.sh script creates an OS specific directory with all of the JVM and native dependencies.
It assumes that the native shared libs for TPK and csw-c are already installed in /usr/local/lib
and copies them to install/tcs-vslice-04/lib/`uname`, where `uname` is Darwin for MacOS, or Linux.

This was tested on Ubuntu-21.04 and MacOS-12.

## Running the pk assembly

To run the assemblies, run: 
    
    csw-services start  # Note: Make sure you are using the version for csw-4.0.0-M1 or greater

    sbt stage

    ./target/universal/stage/bin/tcs-deploy --local ./tcs-deploy/src/main/resources/McsEncPkContainer.conf

To send a command to the pk assembly, you can use the tcs-client command line application:

```
tcs-client 0.0.1
Usage: tcs-client [options]

  -c, --command <command>  The command to send to the pk assembly (One of: SlewToTarget, SetOffset. Default: SlewToTarget)
  -r, --ra <RA>            The RA coordinate for the command in the format hh:mm:ss.sss
  -d, --dec <Dec>          The Dec coordinate for the command in the format dd:mm:ss.sss
  -f, --frame <frame>      The frame of refererence for RA, Dec: (default: None)
  --pmx <pmx>              The primary motion x value: (default: 0.0)
  --pmy <pmy>              The primary motion y value: (default: 0.0)
  -x, --x <x>              The x offset in arcsec: (default: 0.0)
  -y, --y <y>              The y offset in arcsec: (default: 0.0)
  -o, --obsId <id>         The observation id: (default: None)
  -s, --subscribe <value>  Subscribe to all events published here
  --help
  --version
```

Example:
```
./target/universal/stage/bin/tcs-client -c SlewToTarget --ra 10:11:12 --dec 15:21:22
./target/universal/stage/bin/tcs-client -c SlewToTarget --ra 12:11:12 --dec 13:21:22
```

To see the events being fired from the C/C++ code, you can run the tcs-client with the `--subscribe true` option, 
which causes it to subscribes to events and displays them on stdout.

Or, for a more user-friendly view, you can run the [CSW Event Monitor](https://github.com/tmtsoftware/csw-event-monitor)
and display the event values in tables or charts.
This requires also running the following services:

* __esw-services__ start (from [esw](https://github.com/tmtsoftware/esw))
* __icdwebserver__ (from [icd](https://github.com/tmtsoftware/icd)) - with slightly
  modified version of the [TCS-Model-Files](https://github.com/tmt-icd/TCS-Model-Files) repository
  (branch: `tcs-vslice-04-test`) imported manually

In the current version these events are published:

* TCS.PointingKernelAssembly.M3DemandPosition
* TCS.PointingKernelAssembly.EnclosureDemandPosition
* TCS.PointingKernelAssembly.MountDemandPosition
* TCS.MCSAssembly.TCS Telescope Position
* TCS.ENCAssembly.CurrentPosition

The event prefixes and names are based on the TCS API as defined in the above TCS-Model-Files repo.

