#pragma once

#include <cstdio>
#include <iostream>
#include <csw/csw.h>
#include "tpk/tpk.h"
#include "ScanTask.h"

namespace tpkJni {

//    // Holds a callback method for demands
//    class IDemandsCB {
//    public:
//        virtual void newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T) {
//            std::cout << "IDemandsCB::newDemands()" << std::endl;
//        }
//
//        virtual ~IDemandsCB() {
//            std::cout << "IDemandsCB::~IDemandsCB()" << std::endl;
//        }
//    };

    // Used to access a limited set of TPK functions from Scala/Java
    class TpkC {
    public:
        TpkC() { printf("TpkC::TpkC()\n"); }

        ~TpkC() { printf("TpkC::TpkC()\n"); }

        void init();

//        void _register(IDemandsCB *demandsNotify);

        void newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T);

        void publishMcsDemand(double mAz, double mEl);

        void newTarget(double ra, double dec);

        void offset(double raO, double decO);

    private:
//        IDemandsCB *demandsNotifier = nullptr;
        tpk::TmtMountVt *mount = nullptr;
        tpk::TmtMountVt *enclosure = nullptr;
        tpk::Site *site = nullptr;
        CswEventServiceContext publisher = nullptr;
    };
}
