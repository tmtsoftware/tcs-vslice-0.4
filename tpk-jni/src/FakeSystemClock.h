/// \file FakeSystemClock.h
/// \brief Definition of the System clock class.

// Based on SystemCLock, D L Terrett
// Copyright STFC. All rights reserved.

#ifndef tpkFAKESYSTEMCLOCK_H
#define tpkFAKESYSTEMCLOCK_H

#include <chrono>

#include "Clock.h"


/// A Clock that reads the system clock
/**
    The FakeSystemClock is an implementation of Clock that reads the system
    clock which is a assumed to be some fixed number of seconds offset
    from TAI.
*/
class FakeSystemClock : public tpk::Clock {
public:

    /// Constructor with offset
    /**
        The offset parameter defines the time difference between the system
        clock and TAI (in the sense TAI - clock). For a clock running on UTC
        the offset is currently (June 2015) 35s and increases by 1s every
        time UTC undergoes a leap second. It will increase to 36s on 2015
        June 31.

        For a clock synchronized with GPS time the offset is a fixed 19s.

        \note The constructor is not thread safe because it uses gmtime()
        which uses a static buffer.
    */
    explicit FakeSystemClock(
            const double &offset = 0.0  ///< system clock minus TAI (seconds)
    );

    /// Read system clock
    /**
        Reads the system clock and returns the current TAI as a
        Modified Julian Date.

        \returns TAI (MJD)
    */
    double read(void) override;

protected:

    /// Time at which the clock was created
    std::chrono::time_point<std::chrono::system_clock> mTimeZero;

    /// MJD (TAI) of the zero time.
    double mMjdZero;
};

#endif

