#include "TpkC.h"

#include <ctime>
#include "tpk/UnixClock.h"
#include "csw/csw.h"

using std::cin;
using std::cout;
using std::endl;

// multiply to convert degrees to milliarcseconds
const double d2Mas = 60 * 60 * 1000;

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

        double tAz = 180.0 - (mount.roll() / tpk::TcsLib::d2r);
        double tEl = mount.pitch() / tpk::TcsLib::d2r;

        double m3R = mount.m3Azimuth() / tpk::TcsLib::d2r;
        double m3T = 90 - (mount.m3Elevation() / tpk::TcsLib::d2r);

        // Get the enclosure az, el demands in degrees.
        double eAz = 180.0 - (enclosure.roll() / tpk::TcsLib::d2r);
        double eEl = enclosure.pitch() / tpk::TcsLib::d2r;

        // Get the time stamp associated with the demands (a MJD).
//            double t = mount.roll().timestamp();

        if (tpkC)
            tpkC->newDemands(tAz, tEl, eAz, eEl, m3R, m3T);
    }

public:
    FastScan(TpkC *pk, tpk::TimeKeeper &t, tpk::TmtMountVt &m, tpk::TmtMountVt &e) :
            ScanTask(10, 1), tpkC(pk), time(t), mount(m), enclosure(e) {

    };
};

TpkC::TpkC() { printf("TpkC::TpkC()\n"); }

TpkC::~TpkC() { printf("TpkC::TpkC()\n"); }


// Allan: Args are the mount az, el and M3 demands in degrees
void TpkC::newDemands(double mcsAz, double mcsEl, double ecsAz, double ecsEl, double m3Rotation,
                      double m3Tilt) {
    // Allan: Taken from the TPK_POC java callback code (Here we post an event instead of using the callback)
    double ci = 32.5;
    double ciz = 90 - ci;
    double phir = M_PI * ci / 180;
    double tci = tan(ci);
    double cci = cos(ci);
    double PI2 = M_PI * 2;

    // Convert eAz, eEl into base & cap coordinates
    double azShift, base1, cap1; //, base2, cap2;
    if ((ecsEl > PI2) || (ecsEl < 0))
        ecsEl = 0;
    if ((ecsAz > PI2) || (ecsAz < 0))
        ecsAz = 0;

    cap1 = acos(tan(ecsEl - ciz) / tci);
//        cap2 = PI2 - cap1;

    if (ecsEl == PI2)
        azShift = 0;
    else
        azShift = atan(sin(cap1) / cci * (1 - cos(cap1)));

    if ((ecsAz + azShift) > PI2)
        base1 = (ecsAz + azShift) - PI2;
    else
        base1 = ecsAz + azShift;

//        if (ecsAz < azShift)
//            base2 = PI2 + ecsAz - azShift;
//        else
//            base2 = ecsAz - azShift;

    base1 = 180 * base1 / M_PI;
    cap1 = 180 * cap1 / M_PI;

    // base 1 & 2 and cap 1 & 2 can be used for CW, CCW and shortest path
    // for now just base 1 and cap 1 are used

    // Below condition will help in preventing TPK Default Demands
    // from getting published and Demand Publishing will start only
    // once New target or Offset Command is being received
    if (publishDemands) {
        publishMcsDemand(mcsAz, mcsEl);
        publishEcsDemand(base1, cap1);
        publishM3Demand(m3Rotation, m3Tilt);
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
    values[0] = cswMakeAltAzCoord("BASE", lround(az * d2Mas), lround(el * d2Mas));
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


void TpkC::init() {

    // Set up standard out so that it formats double with the maximum precision.
    cout.precision(14);
    cout.setf(std::ios::showpoint);

    // Construct the TCS. First we need a clock...
    tpk::UnixClock clock(
            37.0            // Assume that the system clock is st to UTC.
            // TAI-UTC is 37 sec at the time of writing
    );

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
    tpk::TimeKeeper time(clock, *site);

    // Create a transformation that converts mm to radians for a 450000.0mm	 focal length.
    tpk::AffineTransform transf(0.0, 0.0, 1.0 / 450000.0, 0.0);

    // Create mount and enclosure virtual telescopes.
    // M3 comes automatically with TmtMountVt

    mount = new tpk::TmtMountVt(time, *site, tpk::BentNasmyth(tpk::TcsLib::pi, 0.0), &transf, nullptr,
                                tpk::ICRefSys());
    enclosure = new tpk::TmtMountVt(time, *site, tpk::BentNasmyth(tpk::TcsLib::pi, 0.0), &transf, nullptr,
                                    tpk::ICRefSys());

    // Create and install a pointing model. In a real system this would be initialised from a file.
    tpk::PointingModel model;
    mount->newPointingModel(model);
    enclosure->newPointingModel(model);

    // Make ourselves a real-time process if we have the privilege.
    ScanTask::makeRealTime();

    // Create the slow, medium and fast threads.
    SlowScan slow(time, *site);
    MediumScan medium(*mount, *enclosure);
    FastScan fast(this, time, *mount, *enclosure);

    // Start the scheduler thread.
    ScanTask::startScheduler();



    // Set the field orientation.
    mount->setPai(0.0, tpk::ICRefSys());
    enclosure->setPai(0.0, tpk::ICRefSys());

    // XXX TODO FIXME
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
    tpk::ICRSTarget target(*site, ra * tpk::TcsLib::d2r, dec * tpk::TcsLib::d2r);
    //
    // Set the mount and enclosure to the same target
    //
    mount->newTarget(target);
    enclosure->newTarget(target);
}

void TpkC::offset(double raO, double decO) {
    // Send an offset to the mount
    // arcsec to radians
    const auto as2r = tpk::TcsLib::as2r;
    auto *offset = new tpk::Offset(tpk::xycoord(
            raO * as2r, decO * as2r), tpk::ICRefSys());
    mount->setOffset(*offset);
}

// --- This provides access from C, to make it easier to access from Java ---

extern "C" TpkC *tpkc_ctor() {
    return new TpkC();
}

extern "C" void tpkc_init(TpkC *self) {
    self->init();
}

extern "C" void tpkc_newDemands(TpkC *self, double mAz, double mEl, double eAz, double eEl, double m3R, double m3T) {
    self->newDemands(mAz, mEl, eAz, eEl, m3R, m3T);
}

extern "C" void tpkc_newTarget(TpkC *self, double ra, double dec) {
    self->newTarget(ra, dec);
}

extern "C" void tpkc_offset(TpkC *self, double raO, double decO) {
    self->offset(raO, decO);
}

// ---
