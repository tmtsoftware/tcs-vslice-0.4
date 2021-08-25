#include "TpkC.h"

#include <ctime>
#include "tpk/UnixClock.h"
#include "csw/csw.h"

// Convert degrees to microarcseconds
static double deg2Mas(double d) { return d * 60.0 * 60.0 * 1000.0 * 1000.0; }

// Convert degrees to radians
static double deg2Rad(double d) { return d * M_PI / 180.0; }

// Convert radians to degrees
static double rad2Deg(double d) { return d * 180.0 / M_PI; }

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
            ScanTask(6000, 3), time(t), site(s) {};
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
            ScanTask(500, 2), mount(m), enclosure(e) {};
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
            ScanTask(10, 1), tpkC(pk), time(t), mount(m), enclosure(e) {

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


// Called when there are new demands: All args are in deg
void TpkC::newDemands(double mcsAzDeg, double mcsElDeg, double ecsAzDeg, double ecsElDeg, double m3RotationDeg,
                      double m3TiltDeg) {
    double az = deg2Rad(ecsAzDeg);
    double el = deg2Rad(ecsElDeg);
    double ci = 32.5;
    // Cap inclination fixed enclosure cap inclination
    double ciz = 90.0 - ci;
    double PI2 = M_PI * 2;

    // Convert eAz, eEl into base & cap coordinates
    if ((el > PI2) || (el < 0)) {
        el = 0;
    }
    if ((az > PI2) || (az < 0)) {
        az = 0;
    }

    double cap1 = acos(tan(el - ciz) / tan(ci));
    // check for division by zero from altitude angle at 90 degrees
    double azShift = (el == M_PI_2) ? 0.0 : atan(sin(cap1) / cos(ci) * (1 - cos(cap1)));
    double base1 = ((az + azShift) > PI2) ? (az + azShift) - PI2 : az + azShift;

    // Below condition will help in preventing TPK Default Demands
    // from getting published and Demand Publishing will start only
    // once New target or Offset Command is being received
    if (publishDemands) {
        publishMcsDemand(mcsAzDeg, mcsElDeg);
        publishEcsDemand(rad2Deg(base1), rad2Deg(cap1));
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

// ra and dec are expected in degrees
void TpkC::newTarget(double ra, double dec) {
    publishDemands = true;
    // XXX TODO FIXME: Pass in refFrame and use target subclass based on that!
    tpk::ICRSTarget target(*site, deg2Rad(ra), deg2Rad(dec));
    //
    // Set the mount and enclosure to the same target
    //
    mount->newTarget(target);
    enclosure->newTarget(target);
}

void TpkC::offset(double raO, double decO) {
    mount->setOffset(raO * tpk::TcsLib::as2r, decO * tpk::TcsLib::as2r);
    enclosure->setOffset(raO * tpk::TcsLib::as2r, decO * tpk::TcsLib::as2r);
}

RaDec TpkC::current_position() {
//    tpk::spherical telpos = mount->xy2sky(tpk::xycoord(0.0, 0.0), tpk::FK5RefSys(), 1.0);
    tpk::spherical telpos = mount->position();
    RaDec raDec;
    raDec.ra = rad2Deg(telpos.a);
    raDec.dec = rad2Deg(telpos.b);
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

void tpkc_newTarget(TpkC *self, double ra, double dec) {
    self->newTarget(ra, dec);
}

void tpkc_offset(TpkC *self, double raO, double decO) {
    self->offset(raO, decO);
}

// XXX JNR does not currently support returning struct by value!
double tpkc_current_position_ra(TpkC *self) {
    return self->current_position().ra;
}

double tpkc_current_position_dec(TpkC *self) {
    return self->current_position().dec;
}

}

// ---
