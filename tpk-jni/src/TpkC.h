#pragma once

#include <cstdio>
#include <iostream>
#include "tpk/tpk.h"
#include "ScanTask.h"
#include "csw/csw.h"

// The current position in the current ref sys
typedef struct {
    double a, b;
} CurrentPosition;


// Used to access a limited set of TPK functions from Scala/Java
class TpkC {
public:
    TpkC();

    ~TpkC();

    // Disable copy
    TpkC(TpkC const &) = delete;

    TpkC &operator=(TpkC const &) = delete;

    void init();

    void newDemands(double mcsAzDeg, double mcsElDeg, double eAz, double eEl, double m3RotationDeg, double m3TiltDeg);

    void newICRSTarget(double ra, double dec);

    void newFK5Target(double ra, double dec);

    void newAzElTarget(double ra, double dec);

    void setICRSOffset(double raO, double decO);

    void setFK5Offset(double raO, double decO);

    void setAzElOffset(double azO, double elO);

    // Gets the current CurrentPosition position from the mount
    CurrentPosition currentPosition();

    // Calculates base and cap from the az and el coordinates (in deg)
    static void calculateBaseAndCap(double azDeg, double elDeg, double &baseDeg, double &capDeg);

private:
    // Publish CSW events
    void publishMcsDemand(double az, double el);

    void publishEcsDemand(double base, double cap);

    void publishM3Demand(double rotation, double tilt);

    tpk::TimeKeeper *time;
    tpk::TmtMountVt *mount;
    tpk::TmtMountVt *enclosure;
    tpk::Site *site;
    CswEventServiceContext publisher;
    bool publishDemands = false;
    int publishCounter = 0;
};
