package tcs.pk.wrapper

import jnr.ffi._
import TpkC._

object TpkC {

  // Matches C++ version in tpk-jni used to return coordinate positions
  class CoordPair(runtime: Runtime) extends Struct(runtime) {
    var a = new Double
    var b = new Double
  }

  /**
   * Matching interface for the extern "C" API defined in TpkC.cpp in the tpk-jni subproject
   */
  trait TpkExternC {
    def tpkc_ctor(): Pointer

    def tpkc_init(self: Pointer): Unit

    def tpkc_newICRSTarget(self: Pointer, ra: Double, dec: Double): Unit
    def tpkc_newFK5Target(self: Pointer, ra: Double, dec: Double): Unit
    def tpkc_newAzElTarget(self: Pointer, az: Double, el: Double): Unit

    def tpkc_setICRSOffset(self: Pointer, raO: Double, decO: Double): Unit
    def tpkc_setFK5Offset(self: Pointer, raO: Double, decO: Double): Unit
    def tpkc_setAzElOffset(self: Pointer, azO: Double, elO: Double): Unit

//    // Return the current position in the current ref sys (RA, Dec for ICRS, FK5, ...)
//    def tpkc_currentPosition(self: Pointer, @Out @Transient raDec: CoordPair): Unit
//
//    // Convert az,el to ra,dec
//    def tpkc_azElToRaDec(self: Pointer, az: Double, el: Double, @Out @Transient raDec: CoordPair): Unit
  }

  /**
   * Gets a new instance of the TpkC C class, using this interface
   */
  def getInstance(): TpkC = {
    val tpkExternC = LibraryLoader.create(classOf[TpkExternC]).load("tpk-jni")
    val self       = tpkExternC.tpkc_ctor()
    val runtime    = Runtime.getRuntime(tpkExternC)
    new TpkC(tpkExternC, self, runtime)
  }
}

class TpkC(val tpkExternC: TpkExternC, val self: Pointer, runtime: Runtime) {
  def init(): Unit = {
    tpkExternC.tpkc_init(self)
  }

  def newICRSTarget(ra: Double, dec: Double): Unit = {
    tpkExternC.tpkc_newICRSTarget(self, ra, dec)
  }
  def newFK5Target(ra: Double, dec: Double): Unit = {
    tpkExternC.tpkc_newFK5Target(self, ra, dec)
  }
  def newAzElTarget(az: Double, el: Double): Unit = {
    tpkExternC.tpkc_newAzElTarget(self, az, el)
  }

  def setICRSOffset(raO: Double, decO: Double): Unit = {
    tpkExternC.tpkc_setICRSOffset(self, raO, decO)
  }
  def setFK5Offset(raO: Double, decO: Double): Unit = {
    tpkExternC.tpkc_setFK5Offset(self, raO, decO)
  }
  def setAzElOffset(azO: Double, elO: Double): Unit = {
    tpkExternC.tpkc_setAzElOffset(self, azO, elO)
  }

//  // The mount's current ra,dec position  (if using ICRS, FK5) as a pair (ra, dec) in deg
//  def currentPosition(): (Double, Double) = {
//    val raDec = new CoordPair(runtime)
//    tpkExternC.tpkc_currentPosition(self, raDec)
//    (raDec.a.get(), raDec.b.get())
//  }
//
//  // Converts the given az,el coords (in deg) to ra,dec and returns a pair (ra, dec) in deg
//  def azElToRaDec(az: Double, el: Double): (Double, Double) = {
//    val raDec = new CoordPair(runtime)
//    tpkExternC.tpkc_azElToRaDec(self, az, el, raDec)
//    (raDec.a.get(), raDec.b.get())
//  }

}
