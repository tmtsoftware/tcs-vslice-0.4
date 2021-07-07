#pragma once

namespace tpkJni {
    // Used to access a limited set of TPK functions from Scala/Java (C compat version for swig)
    class TpkC {
    public:
        TpkC();

        ~TpkC();

        void init();

        void newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T);

        void newTarget(double ra, double dec);

        void offset(double raO, double decO);
    };
}
