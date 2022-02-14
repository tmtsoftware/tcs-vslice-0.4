#pragma once

#include <cstdio>
#include <iostream>
#include "tpk/tpk.h"
#include "ScanTask.h"
#include "csw/csw.h"

// Used to store coordinates (az,el or ra,dec) in deg
typedef struct {
    double a, b;
} CoordPair;


// Used to access a limited set of TPK functions from Scala/Java
class TpkC {
public:
    TpkC();

    ~TpkC();

    // Disable copy
    TpkC(TpkC const &) = delete;

    TpkC &operator=(TpkC const &) = delete;

    // Initialize the class (called from the Scala pk assembly code)
    void init();

    // Stops publishing demands
    void shutdown();

    void newDemands(double mcsAzDeg, double mcsElDeg, double eAz, double eEl, double m3RotationDeg, double m3TiltDeg, double raDeg, double decDeg);

    // Sets a new ICRS target with RA, Dec in deg and returns true if the target is above the horizon
    bool newICRSTarget(double ra, double dec);

    // Sets a new ICRS target with RA, Dec in deg and returns true if the target is above the horizon
    bool newFK5Target(double ra, double dec);

    // Sets a new AzEl target with az, el in deg and returns true if the target is above the horizon
    bool newAzElTarget(double ra, double dec);

    // Set the offset. raO and decO are expected in arcsec
    void setICRSOffset(double raO, double decO);

    // Set the offset. raO and decO are expected in arcsec
    void setFK5Offset(double raO, double decO);

    // Set the offset. azO and elO are expected in arcsec
    void setAzElOffset(double azO, double elO);

    // Gets the current CurrentPosition position from the mount as RA, Dec in deg
    void currentPosition(CoordPair* raDec);

    // Convert the given az,el coordinates (in deg) to ra,dec (in deg)
    void azElToRaDec(double az, double el, CoordPair* raDec);

    // Convert the given ra,dec coordinates (in deg) to az,el (in deg)
    void raDecToAzEl(double ra, double dec, CoordPair *azEl);

    // Calculates base and cap from the az and el coordinates (in deg)
    static void calculateBaseAndCap(double azDeg, double elDeg, double &baseDeg, double &capDeg);

    // Returns true if the base and cap values for the given az,el pos in deg can be calculated
    static bool isTargetVisible(double azDeg, double elDeg);

private:
    // Publish CSW events
    void publishMcsDemand(double az, double el, double ra, double dec);

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
