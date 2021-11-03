# TCS Vertical Slice Demo

This project contains demo CSW assemblies and test client code based on the TCS API
as defined the the [ICD Database](https://github.com/tmtsoftware/icd).

It makes use of the Scala based CSW framework, and also links with native C/C++ code
(using [JNR - Java Native Runtime](https://github.com/jnr/jnr-ffi/blob/master/docs/README.md)).
For example, the pk (Pointing Kernel) assembly receives a `SlewToTarget` command and then
makes a call to C/C++ code that calls [TPK](https://github.com/tmtsoftware/TPK) routines to set a target and
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

## Running the pk assembly

To run the assemblies, run:

    csw-services start  # Note: Make sure you are using the version for csw-4.0.0-M1 or greater
    bin/tcs-deploy --local conf/McsEncPkContainer.conf

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

    bin/tcs-client -c SlewToTarget --ra 05:11:12 --dec 30:21:22

To see the events being fired from the C/C++ code, you can run the tcs-client with the `--subscribe true` option,
which causes it to subscribes to events and displays them on stdout.

Or, for a more user-friendly view, you can run the [CSW Event Monitor](https://github.com/tmtsoftware/csw-event-monitor)
and display the event values in tables or charts.
This requires also running the following services:

* __esw-services__ start (from [esw](https://github.com/tmtsoftware/esw))
* __icdwebserver__ (from [icd](https://github.com/tmtsoftware/icd))

