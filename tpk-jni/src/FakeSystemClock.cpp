/// \file FakeSystemClock.cpp
/// \brief Implementation of the System clock class.

// D L Terrett
// Copyright STFC. All rights reserved.

#include "FakeSystemClock.h"

#include <cassert>
#include <chrono>
#include <ctime>

#include "slalib.h"

/*****************************************************************************/
/*
    The constructor stores a time as a chrono time point and as a MJD in TAI.

    The actual time is unimportant but is, in fact, the current time truncated
    to an integral number of seconds.
*/
FakeSystemClock::FakeSystemClock(const double &offset) {

    // Convert now to a time_t
    auto t_clock = std::chrono::system_clock::now();
    time_t t = std::chrono::system_clock::to_time_t(t_clock);

    // Convert the time_t back to a time point
    mTimeZero = std::chrono::system_clock::from_time_t(t);

    // Convert the time to a calendar data
    std::tm *tm = std::gmtime(&t);

    // XXX Allan: Commenting out this part during testing to make results predictable
//    // Convert this date to a MJD
//    int j;
//    slaCldj(1900 + tm->tm_year, 1 + tm->tm_mon, tm->tm_mday, &mMjdZero, &j);
//    assert(j == 0);
//
//    // Add the time of day
//        mMjdZero += ( tm->tm_hour + ( tm->tm_min + tm->tm_sec / 60.0 ) /
//                60.0 ) / 24.0;
//
//    // Add the offset between the system clock and TAI.
//    mMjdZero += offset / 86400.0;

    // XXX Allan: Set to midnight, Jan 1, 2022
    mMjdZero = 59580;
}

/*****************************************************************************/

double FakeSystemClock::read(void) {

    // Read the system clock.
    auto now = std::chrono::system_clock::now();

    // Seconds since the zero time
    std::chrono::duration<double> s = now - mTimeZero;

    // Convert to MJD.
    return mMjdZero + s.count() / 86400.0;
}

/*****************************************************************************/

