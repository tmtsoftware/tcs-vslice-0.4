# Change Log
All notable changes to this project will be documented in this file.

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

