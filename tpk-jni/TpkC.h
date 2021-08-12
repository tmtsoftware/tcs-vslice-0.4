#pragma once

#include <cstdio>
#include <iostream>
#include "tpk/tpk.h"
#include "ScanTask.h"
#include "csw/csw.h"


// Used to access a limited set of TPK functions from Scala/Java
class TpkC {
public:
    TpkC();

    ~TpkC();

    // Disable copy
    TpkC(TpkC const&) = delete;
    TpkC& operator=(TpkC const&) = delete;

    void init();

    void newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T);

    void newTarget(double ra, double dec);

    void offset(double raO, double decO);

private:
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
