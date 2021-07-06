#pragma once

#include <cstdio>
#include <iostream>
#include <csw/csw.h>
#include "tpk/tpk.h"
#include "ScanTask.h"

namespace tpkJni {
    // Used to access a limited set of TPK functions from Scala/Java
    class TpkC {
    public:
        TpkC() { printf("TpkC::TpkC()\n"); }

        ~TpkC() { printf("TpkC::TpkC()\n"); }

        void init();

        void newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T);

        void newTarget(double ra, double dec);

        void offset(double raO, double decO);

    private:
        void publishMcsDemand(double az, double el);
        void publishEcsDemand(double base, double cap);
        void publishM3Demand(double rotation, double tilt);

        tpk::TmtMountVt *mount = nullptr;
        tpk::TmtMountVt *enclosure = nullptr;
        tpk::Site *site = nullptr;
        CswEventServiceContext publisher = nullptr;
        bool publishDemands = false;
    };
}
