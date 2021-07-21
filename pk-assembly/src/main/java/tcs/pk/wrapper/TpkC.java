//package tcs.pk.wrapper;
//
//import jnr.ffi.LibraryLoader;
//import jnr.ffi.Pointer;
//
//public class TpkC {
//  private final TpkExternC tpkExternC;
//  private final Pointer self;
//
//  TpkC(TpkExternC tpkExternC, Pointer self) {
//    this.tpkExternC = tpkExternC;
//    this.self = self;
//  }
//
//  public void init() {
//    tpkExternC.tpkc_init(self);
//  }
//
//  public void newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T) {
//    tpkExternC.tpkc_newDemands(self, mAz, mEl, eAz, eEl, m3R, m3T);
//  }
//
//  public void newTarget(double ra, double dec) {
//    tpkExternC.tpkc_newTarget(self, ra, dec);
//  }
//
//  public void offset(double raO, double decO) {
//    tpkExternC.tpkc_offset(self, raO, decO);
//  }
//
//  /**
//   * Gets a new instance of the TpkC C class, using this interface
//   */
//  public static TpkC getInstance() {
//    TpkExternC tpkExternC = LibraryLoader.create(TpkExternC.class).load("tpk-jni");
//    Pointer self = tpkExternC.tpkc_ctor();
//    return new TpkC(tpkExternC, self);
//  }
//}
