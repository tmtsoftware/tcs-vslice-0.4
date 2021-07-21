package tcs.pk.wrapper;

import jnr.ffi.Pointer;

/**
 * Matching interface for the extern "C" API defined in TpkC.cpp in the tpk-jni subproject
 */
public interface TpkExternC {
  public Pointer tpkc_ctor();

  public void tpkc_init(Pointer self);

  public void tpkc_newDemands(Pointer self, double mAz, double mEl, double eAz, double eEl, double m3R, double m3T);

  public void tpkc_newTarget(Pointer self, double ra, double dec);

  public void tpkc_offset(Pointer self, double raO, double decO);
}
