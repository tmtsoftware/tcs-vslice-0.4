#include "interface.h"

#include <ctime>
#include "tpk/UnixClock.h"
#include "csw/csw.h"

using std::cin;
using std::cout;
using std::endl;
using std::string;

namespace tpkJni {

    // multiply to convert degrees to milliarcseconds
    const double d2Mas = 60 * 60 * 1000;

    // CSW component prefix
    const char* prefix = "TCS.pk";

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

    // -- Tests publishing coordinate values --
    static void publishAltAzCoord(CswEventServiceContext publisher) {
        CswAltAzCoord altAzCoord1 = cswMakeAltAzCoord("BASE", 1083600000000, 153000000000);
        CswCoord values[1];
        values[0].altAzCoord = altAzCoord1;
        CswArrayValue arrayValues = {.values = values, .numValues = 1};
        CswParameter coordParam = cswMakeParameter("CoordParam", CoordKey, arrayValues, "NoUnits");

        // -- ParamSet
        CswParameter params[] = {coordParam};
        CswParamSet paramSet = {.params = params, .numParams = 1};

        // -- Event --
        CswEvent event = cswMakeEvent(SystemEvent, prefix, "AltAzCoordEvent", paramSet);

        // -- Publish --
        cswEventPublish(publisher, event);

        // -- Cleanup --
        cswFreeEvent(event);
    }

    void TpkC::newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T) {
        CswAltAzCoord values[3];
        values[0] = cswMakeAltAzCoord("mount", lround(mAz * d2Mas), lround(mEl * d2Mas));
        values[1] = cswMakeAltAzCoord("enclosure", lround(eAz * d2Mas), lround(eEl * d2Mas));
        values[2] = cswMakeAltAzCoord("enclosure", lround(m3R * d2Mas), lround(m3T * d2Mas));
        CswArrayValue arrayValues = {.values = values, .numValues = 3};
        CswParameter coordParam = cswMakeParameter("demandPositions", AltAzCoordKey, arrayValues, "NoUnits");

        // -- ParamSet
        CswParameter params[] = {coordParam};
        CswParamSet paramSet = {.params = params, .numParams = 1};

        // -- Event --
        CswEvent event = cswMakeEvent(SystemEvent, prefix, "demands", paramSet);

        // -- Publish --
        cswEventPublish(publisher, event);

        // -- Cleanup --
        cswFreeEvent(event);

        // XXX TODO FIXME: Replace with posting event
//        if (demandsNotifier != nullptr) {
//            demandsNotifier->newDemands(mAz, mEl, eAz, eEl, m3R, m3T);
//        }


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

//    void TpkC::_register(IDemandsCB *demandsNotify) {
//        demandsNotifier = demandsNotify;
//        if (demandsNotifier == nullptr) printf("Register failed\n");
//    }

    void TpkC::newTarget(double ra, double dec) {
        tpk::ICRSTarget target(*site, ra * tpk::TcsLib::d2r, dec * tpk::TcsLib::d2r);
        //tpk::ICRSTarget target(*site, "12 23 11 06 45 12");
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

}

