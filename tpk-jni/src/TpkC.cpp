#include "TpkC.h"

#include <ctime>
#include <cmath>
#include "tpk/UnixClock.h"
#include "csw/csw.h"

// Convert degrees to microarcseconds
static double deg2Mas(double d) { return d * 60.0 * 60.0 * 1000.0 * 1000.0; }

// Convert degrees to radians
#define deg2Rad(d) ((d) * M_PI / 180.0)

// Convert radians to degrees
#define rad2Deg(d) ((d) * 180.0 / M_PI)

// CSW component prefix
const char *prefix = "TCS.PointingKernelAssembly";

// The SlowScan class implements the "slow" loop of the application.

class SlowScan : public ScanTask {
private:
    tpk::TimeKeeper &time;
    tpk::Site &site;

    void scan() override {

        // Update the Site object with the current time. If we had a weather
        // server we would also update the atmospheric conditions here.
        site.refresh(time.tai());
    }

public:
    SlowScan(tpk::TimeKeeper &t, tpk::Site &s) :
            ScanTask("/SlowScan", 6000, 3), time(t), site(s) {};
};

// The MediumScan class implements the "medium" loop.

class MediumScan : public ScanTask {
private:
    tpk::TmtMountVt mount;
    tpk::TmtMountVt enclosure;

    void scan() override {

        // Update the pointing model.
        mount.updatePM();

        // Update the mount SPMs,
        mount.update();

        // Update the pointing model.
        enclosure.updatePM();

        // Update the enclosure SPMs,
        enclosure.update();
    }

public:
    MediumScan(tpk::TmtMountVt &m, tpk::TmtMountVt &e) :
            ScanTask("/MediumScan", 500, 2), mount(m), enclosure(e) {};
};

// The FastScan class implements the "fast" loop.
class FastScan : public ScanTask {
private:
    TpkC *tpkC;
    tpk::TimeKeeper &time;
    tpk::TmtMountVt &mount;
    tpk::TmtMountVt &enclosure;


    void scan() override {
        // Update the time
        time.update();

        // Compute the mount and rotator position demands.
        mount.track(1);

        // Compute the enclosure  position demands.
        enclosure.track(1);

        // Get the mount az, el and M3 demands in degrees.

        double tAz = 180.0 - rad2Deg(mount.roll());
        double tEl = rad2Deg(mount.pitch());

        // Get the enclosure az, el demands in degrees.
        double eAz = 180.0 - rad2Deg(enclosure.roll());
        double eEl = rad2Deg(enclosure.pitch());

        double m3R = rad2Deg(mount.m3Azimuth());
        double m3T = 90.0 - rad2Deg(mount.m3Elevation());

        if (tpkC)
            tpkC->newDemands(tAz, tEl, eAz, eEl, m3R, m3T);
    }

public:
    FastScan(TpkC *pk, tpk::TimeKeeper &t, tpk::TmtMountVt &m, tpk::TmtMountVt &e) :
            ScanTask("/FastScan", 10, 1), tpkC(pk), time(t), mount(m), enclosure(e) {

    };
};

TpkC::TpkC() {
    // These fields are initialized in init()
    time = nullptr;
    site = nullptr;
    publisher = nullptr;
    mount = nullptr;
    enclosure = nullptr;
}

TpkC::~TpkC() {
    delete time;
    delete site;
    cswEventPublisherClose(publisher);
    delete mount;
    delete enclosure;
}

static const double ci = deg2Rad(32.5);
static const double ciz = deg2Rad(90.0 - 32.5);
static const double PI2 = M_PI * 2;


// From table:
// cap  =ROUND(DEGREES(ACOS(TAN(RADIANS(el-57.5)) / TAN(RADIANS(32.5)))),1)
// base =ROUND(DEGREES(ATAN(SIN(RADIANS(cap)/COS(RADIANS(32.5))*(1-COS(RADIANS(cap)))))),1)
// ???  =ROUND(DEGREES(ACOS(TAN(RADIANS(A2-57.5+1)) / TAN(RADIANS(32.5)))),1)

// Calculates the base and cap values
void TpkC::calculateBaseAndCap(double ecsAzDeg, double ecsElDeg, double &baseDeg, double &capDeg) {
    double azRad = deg2Rad(ecsAzDeg);
    double elRad = deg2Rad(ecsElDeg);

    if ((elRad > PI2) || (elRad < 0)) {
        elRad = 0;
    }
    if ((azRad > PI2) || (azRad < 0)) {
        azRad = 0;
    }

    // Convert Az, El into base & cap coordinates
    double capRad = acos(tan(elRad - ciz) / tan(ci));
    // check for division by zero from altitude angle at 90 degrees
    double azShift = (elRad == M_PI_2) ? 0.0 : atan(sin(capRad / cos(ci) * (1 - cos(capRad))));
    double baseRad = ((azRad + azShift) > PI2) ? (azRad + azShift) - PI2 : azRad + azShift;
    baseDeg = rad2Deg(baseRad);
    capDeg = rad2Deg(capRad);
}

// Called when there are new demands: All args are in deg
void TpkC::newDemands(double mcsAzDeg, double mcsElDeg, double ecsAzDeg, double ecsElDeg, double m3RotationDeg,
                      double m3TiltDeg) {
    double baseDeg, capDeg;
    calculateBaseAndCap(ecsAzDeg, ecsElDeg, baseDeg, capDeg);

    // Below condition will help in preventing TPK Default Demands
    // from getting published and Demand Publishing will start only
    // once New target or Offset Command is being received
    // Note from doc: Mount accepts demands at 100Hz and enclosure accepts demands at 20Hz
    if (publishDemands) {
        publishMcsDemand(mcsAzDeg, mcsElDeg);
        if (!(std::isnan(baseDeg) || std::isnan(capDeg)) && ++publishCounter % 5 == 0) {
            publishCounter = 0;
            publishEcsDemand(baseDeg, capDeg);
        }
        publishM3Demand(m3RotationDeg, m3TiltDeg);
    }
}

// Publish a TCS.PointingKernelAssembly.MountDemandPosition event to the CSW event service.
// Args are in degrees.
void TpkC::publishMcsDemand(double az, double el) {
    // trackID
    const char *trackIdAr[] = {"trackid-0"}; // TODO
    CswArrayValue trackIdValues = {.values = trackIdAr, .numValues = 1};
    CswParameter trackIdParam = cswMakeParameter("trackID", StringKey, trackIdValues, csw_unit_NoUnits);

    // pos
    CswAltAzCoord values[1];
    values[0] = cswMakeAltAzCoord("BASE", lround(deg2Mas(az)), lround(deg2Mas(el)));
    CswArrayValue arrayValues = {.values = values, .numValues = 1};
    CswParameter coordParam = cswMakeParameter("pos", AltAzCoordKey, arrayValues, csw_unit_NoUnits);

    // time
    CswUtcTime timeAr[] = {cswUtcTime()};
    CswArrayValue timeValues = {.values = timeAr, .numValues = 1};
    CswParameter timeParam = cswMakeParameter("time", UTCTimeKey, timeValues, csw_unit_NoUnits);


    // -- ParamSet
    CswParameter params[] = {trackIdParam, coordParam, timeParam};
    CswParamSet paramSet = {.params = params, .numParams = 3};

    // -- Event --
    CswEvent event = cswMakeEvent(SystemEvent, prefix, "MountDemandPosition", paramSet);

    // -- Publish --
    cswEventPublish(publisher, event);

    // -- Cleanup --
    cswFreeEvent(event);
}

// Publish a TCS.PointingKernelAssembly.EnclosureDemandPosition event to the CSW event service.
// base and cap are in degrees
void TpkC::publishEcsDemand(double base, double cap) {
    // trackID
    const char *trackIdAr[] = {"trackid-0"}; // TODO
    CswArrayValue trackIdValues = {.values = trackIdAr, .numValues = 1};
    CswParameter trackIdParam = cswMakeParameter("trackID", StringKey, trackIdValues, csw_unit_NoUnits);

    // BasePosition
    double baseAr[] = {base};
    CswArrayValue baseValues = {.values = baseAr, .numValues = 1};
    CswParameter baseParam = cswMakeParameter("BasePosition", DoubleKey, baseValues, csw_unit_degree);

    // CapPosition
    double capAr[] = {cap};
    CswArrayValue calValues = {.values = capAr, .numValues = 1};
    CswParameter capParam = cswMakeParameter("CapPosition", DoubleKey, calValues, csw_unit_degree);

    // time
    CswUtcTime timeAr[] = {cswUtcTime()};
    CswArrayValue timeValues = {.values = timeAr, .numValues = 1};
    CswParameter timeParam = cswMakeParameter("time", UTCTimeKey, timeValues, csw_unit_NoUnits);


    // -- ParamSet
    CswParameter params[] = {trackIdParam, baseParam, capParam, timeParam};
    CswParamSet paramSet = {.params = params, .numParams = 4};

    // -- Event --
    CswEvent event = cswMakeEvent(SystemEvent, prefix, "EnclosureDemandPosition", paramSet);

    // -- Publish --
    cswEventPublish(publisher, event);

    // -- Cleanup --
    cswFreeEvent(event);
}

// Publish a TCS.PointingKernelAssembly.M3DemandPosition event to the CSW event service.
// rotation and tilt are in degrees
void TpkC::publishM3Demand(double rotation, double tilt) {
    // trackID
    const char *trackIdAr[] = {"trackid-0"}; // TODO
    CswArrayValue trackIdValues = {.values = trackIdAr, .numValues = 1};
    CswParameter trackIdParam = cswMakeParameter("trackID", StringKey, trackIdValues, csw_unit_NoUnits);

    // RotationPosition
    double rotationAr[] = {rotation};
    CswArrayValue rotationValues = {.values = rotationAr, .numValues = 1};
    CswParameter rotationParam = cswMakeParameter("RotationPosition", DoubleKey, rotationValues, csw_unit_degree);

    // BasePosition
    double tiltAr[] = {tilt};
    CswArrayValue calValues = {.values = tiltAr, .numValues = 1};
    CswParameter tiltParam = cswMakeParameter("TiltPosition", DoubleKey, calValues, csw_unit_degree);

    // time
    CswUtcTime timeAr[] = {cswUtcTime()};
    CswArrayValue timeValues = {.values = timeAr, .numValues = 1};
    CswParameter timeParam = cswMakeParameter("time", UTCTimeKey, timeValues, csw_unit_NoUnits);


    // -- ParamSet
    CswParameter params[] = {trackIdParam, rotationParam, tiltParam, timeParam};
    CswParamSet paramSet = {.params = params, .numParams = 4};

    // -- Event --
    CswEvent event = cswMakeEvent(SystemEvent, prefix, "M3DemandPosition", paramSet);

    // -- Publish --
    cswEventPublish(publisher, event);

    // -- Cleanup --
    cswFreeEvent(event);
}

// XXX TODO: Move this to constructor?
void TpkC::init() {
    // Construct the TCS. First we need a clock...
    // Assume that the system clock is st to UTC. TAI-UTC is 37 sec at the time of writing.
    tpk::UnixClock clock(37.0);

    // and a Site...
    site = new tpk::Site(clock.read(),
                         0.56,            // UT1-UTC (seconds)
                         37.0,            // TAI-UTC (seconds)
                         32.184,          // TT-TAI (seconds)
                         -155.4775033,    // East longitude (for Hawaii)
                         19.82900194,     // Latitude (for Hawaii)
                         4160,            // Height (metres) (for Hawaii)
                         0.1611, 0.4475   // Polar motions (arcsec)
    );

    // Get an object for publishing CSW events
    publisher = cswEventPublisherInit();

    // and a "time keeper"...
    time = new tpk::TimeKeeper(clock, *site);

    // Create a transformation that converts mm to radians for a 450000.0mm	 focal length.
    tpk::AffineTransform transf(0.0, 0.0, 1.0 / 450000.0, 0.0);

    // Create mount and enclosure virtual telescopes. M3 comes automatically with TmtMountVt.
    mount = new tpk::TmtMountVt(*time, *site, tpk::BentNasmyth(tpk::TcsLib::pi, 0.0), &transf, nullptr,
                                tpk::ICRefSys());
    enclosure = new tpk::TmtMountVt(*time, *site, tpk::BentNasmyth(tpk::TcsLib::pi, 0.0), &transf, nullptr,
                                    tpk::ICRefSys());
    // Create and install a pointing model. In a real system this would be initialised from a file.
    tpk::PointingModel model;
    mount->newPointingModel(model);
    enclosure->newPointingModel(model);

    // Make ourselves a real-time process if we have the privilege.
    ScanTask::makeRealTime();

    // Create the slow, medium and fast threads.
    SlowScan slow(*time, *site);
    MediumScan medium(*mount, *enclosure);
    FastScan fast(this, *time, *mount, *enclosure);

    // Start the scheduler thread.
    ScanTask::startScheduler();

    // Set the field orientation.
    mount->setPai(0.0, tpk::ICRefSys());
    enclosure->setPai(0.0, tpk::ICRefSys());

    tpk::ICRSTarget target(*site, "10 12 23 11 09 06");

    //
    // Set the mount and enclosure to the same target
    //
    mount->newTarget(target);
    enclosure->newTarget(target);

#pragma clang diagnostic push
#pragma ide diagnostic ignored "EndlessLoop"
    for (;;) {
        nanosleep((const struct timespec[]) {{0, 500000L}}, nullptr);
    }
#pragma clang diagnostic pop
}

// a and b are expected in degrees
void TpkC::newICRSTarget(double ra, double dec) {
    publishDemands = true;
    tpk::ICRSTarget target(*site, deg2Rad(ra), deg2Rad(dec));
    mount->newTarget(target);
    enclosure->newTarget(target);
}

// a and b are expected in degrees
void TpkC::newFK5Target(double ra, double dec) {
    publishDemands = true;
    tpk::FK5Target target(*site, deg2Rad(ra), deg2Rad(dec));
    mount->newTarget(target);
    enclosure->newTarget(target);
}

// az and el are expected in degrees
void TpkC::newAzElTarget(double az, double el) {
    publishDemands = true;
    tpk::AzElTarget target(*site, deg2Rad(az), deg2Rad(el));
    mount->newTarget(target);
    enclosure->newTarget(target);
}

// Set the offset. raO and decO are expected in arcsec
void TpkC::setICRSOffset(double raO, double decO) {
    auto a = raO * tpk::TcsLib::as2r;
    auto b = decO * tpk::TcsLib::as2r;
    auto refSys = tpk::ICRefSys();
    mount->setOffset(a, b, refSys);
    enclosure->setOffset(a, b, refSys);
}

// Set the offset. raO and decO are expected in arcsec
void TpkC::setFK5Offset(double raO, double decO) {
    auto a = raO * tpk::TcsLib::as2r;
    auto b = decO * tpk::TcsLib::as2r;
    auto refSys = tpk::FK5RefSys();
    mount->setOffset(a, b, refSys);
    enclosure->setOffset(a, b, refSys);
}

// Set the offset. azO and elO are expected in arcsec
void TpkC::setAzElOffset(double azO, double elO) {
    auto a = azO * tpk::TcsLib::as2r;
    auto b = elO * tpk::TcsLib::as2r;
    auto refSys = tpk::AzElRefSys();
    mount->setOffset(a, b, refSys);
    enclosure->setOffset(a, b, refSys);
}

CurrentPosition TpkC::currentPosition() {
    tpk::spherical telpos = mount->position();
    CurrentPosition raDec;
    raDec.a = rad2Deg(telpos.a);
    raDec.b = rad2Deg(telpos.b);
    return raDec;
}

// --- This provides access from C, to make it easier to access from Java ---

extern "C" {

TpkC *tpkc_ctor() {
    return new TpkC();
}

void tpkc_init(TpkC *self) {
    self->init();
}

void tpkc_newDemands(TpkC *self, double mAz, double mEl, double eAz, double eEl, double m3R, double m3T) {
    self->newDemands(mAz, mEl, eAz, eEl, m3R, m3T);
}

void tpkc_newICRSTarget(TpkC *self, double ra, double dec) {
    self->newICRSTarget(ra, dec);
}

void tpkc_newFK5Target(TpkC *self, double ra, double dec) {
    self->newFK5Target(ra, dec);
}

void tpkc_newAzElTarget(TpkC *self, double ra, double dec) {
    self->newAzElTarget(ra, dec);
}

void tpkc_setICRSOffset(TpkC *self, double raO, double decO) {
    self->setICRSOffset(raO, decO);
}

void tpkc_setFK5Offset(TpkC *self, double raO, double decO) {
    self->setFK5Offset(raO, decO);
}

void tpkc_setAzElOffset(TpkC *self, double raO, double decO) {
    self->setAzElOffset(raO, decO);
}

// XXX JNR does not currently support returning struct by value!
// Returns the first coordinate of the current pos in the current ref sys (RA of ICRS, FK5, ...)
double tpkc_currentPositionA(TpkC *self) {
    return self->currentPosition().a;
}

// Returns the second coordinate of the current pos in the current ref sys (Dec of ICRS, FK5, ...)
double tpkc_currentPositionB(TpkC *self) {
    return self->currentPosition().b;
}

}

// ---

