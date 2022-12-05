# Change Log
All notable changes to this project will be documented in this file.

## [tcs-vslice v0.10] - 2022-12-05

### Changed

* Updated for csw-5.0.0
* Fix for RA,Dec demand pos generated from native code
* Added /opt/homebrew/lib and /opt/homebrew/include for Mac M1 support
* Updated for CentOS-7 build

## [tcs-vslice v0.9] - 2022-02-23

### Changed

* Updated for latest CSW snapshot (changes in csw framework standalone configs) 
* Added siderealTime (double, hours), demandHourAngle and currentHourAngle (double, degrees) to MCSAssembly's MountPosition event

## [tcs-vslice v0.8] - 2022-02-14

### Changed

* Updated for csw-4.0.1
* The pk assembly SlewToTarget command now responds with an error if the target coordinates are too close to the horizon for observing (The calculations for the base/cap coordinates result in NaN values below 25 degrees elevation).
* The pk assembly now calls a shutdown() method in the native code when the assembly shuts down. This causes the C code to stop publishing demands and close the connection to Redis.

## [tcs-vslice v0.7] - 2022-02-03

### Changed

* Added field "posRaDec" of type EqCoord to the TCS.PointingKernelAssembly's MountDemandPosition event
* Changed the MCS assembly to simulate slewing to target on the basis of the RA,Dec coordinates received from the PointingKernelAssembly
* Added an integration test and standalone test app under tcs-deploy that tests a container with the pk, mcs and enc assemblies.
* Changed the MJD time used when the TPK_USE_FAKE_SYSTEM_CLOCK environment variable is set be midnight Hawaiian time

## [tcs-vslice v0.6] - 2022-01-31

### Changed

* Added "currentPos" and "demandPos" fields (both EqCoord types) to MCSAssembly's MountPosition event, so that you can see the RA,Dec values
* Added "siderealTime" field (double, hours) to the TCS PointingKernelAssembly's "MountDemandPosition" field, so that the alt,az values can be converted to RA,Dec (For latitude, Hawaii is currently hard-coded)
* Fixed a bug in the event fired from the pk assembly (az and el were swapped)
* Updated the TCS-Model-Files repo (branch: tcs-vslice-04-test) with the latest event changes.

## [tcs-vslice v0.5] - 2022-01-10

### Changed

* Added check for environment variable TPK_USE_FAKE_SYSTEM_CLOCK in dynamically loaded shared libtpk-jni.
  (If set, the MJD is forced to start at midnight, Jan 1, 2022, making it easier to make reproducible test cases.)
* Added check that SlewToTarget RA, Dec coordinates are valid

## [tcs-vslice v0.4] - 2021-12-13

### Changed

* In the MCS Assembly, modified the published "TCS Telescope Position" event to include the demand position
* Renamed the "pos" parameter in the "TCS Telescope Position" event to "current" and added a new "demand" position parameter
* Changed the name of the "TCS Telescope Position" event to "MountPosition"
* Renamed the "pos" parameter in the SlewToTarget command in the pk Assembly to "base".
* Modified the enclosure "currentPosition" event to have baseCurrent, baseDemand, capCurrent, capDemand (was just base, cap)
* Fixed a bug in the native C++ code (libtpk-jni) that converted base and cap to the wrong units

## [tcs-vslice v0.3] - 2021-11-22

- Initial release

