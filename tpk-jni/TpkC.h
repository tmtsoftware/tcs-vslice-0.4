#pragma once

#include <cstdio>
#include <iostream>
#include "tpk/tpk.h"
#include "ScanTask.h"
#include "csw/csw.h"

typedef struct {
    double ra, dec;
} RaDec;


// Used to access a limited set of TPK functions from Scala/Java
class TpkC {
public:
    TpkC();

    ~TpkC();

    // Disable copy
    TpkC(TpkC const&) = delete;
    TpkC& operator=(TpkC const&) = delete;

    void init();

    void newDemands(double mcsAzDeg, double mcsElDeg, double eAz, double eEl, double m3RotationDeg, double m3TiltDeg);

    void newICRSTarget(double ra, double dec);
    void newFK5Target(double ra, double dec);
    void newAzElTarget(double ra, double dec);

    void offset(double raO, double decO);

    // Gets the current RaDec position from the mount
    RaDec current_position();

private:
    // Publish CSW events
    void publishMcsDemand(double az, double el);
    void publishEcsDemand(double base, double cap);
    void publishM3Demand(double rotation, double tilt);

    tpk::TimeKeeper* time;
    tpk::TmtMountVt *mount;
    tpk::TmtMountVt *enclosure;
    tpk::Site *site;
    CswEventServiceContext publisher;
    bool publishDemands = false;
};
