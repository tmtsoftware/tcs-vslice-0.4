# Change Log
All notable changes to this project will be documented in this file.

## [tcs-vslice v0.5] - 

### Changed

* Added check for environment variable TPK_USE_FAKE_SYSTEM_CLOCK in dynamically loaded shared libtpk-jni.
  (If set, the MJD is forced to start at midnight, Jan 1, 2022, making it easier to make reproducible test cases.)

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

