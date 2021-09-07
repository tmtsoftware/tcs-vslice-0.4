package tcs.pk.wrapper

import jnr.ffi.{LibraryLoader, Pointer}
import TpkC._

object TpkC {

  /**
   * Matching interface for the extern "C" API defined in TpkC.cpp in the tpk-jni subproject
   */
  trait TpkExternC {
    def tpkc_ctor(): Pointer

    def tpkc_init(self: Pointer): Unit

    def tpkc_newDemands(self: Pointer, mAz: Double, mEl: Double, eAz: Double, eEl: Double, m3R: Double, m3T: Double): Unit

    def tpkc_newICRSTarget(self: Pointer, ra: Double, dec: Double): Unit
    def tpkc_newFK5Target(self: Pointer, ra: Double, dec: Double): Unit
    def tpkc_newAzElTarget(self: Pointer, ra: Double, dec: Double): Unit

    def tpkc_setICRSOffset(self: Pointer, raO: Double, decO: Double): Unit
    def tpkc_setFK5Offset(self: Pointer, raO: Double, decO: Double): Unit
    def tpkc_setAzElOffset(self: Pointer, azO: Double, elO: Double): Unit

    // XXX JNR does not currently support returning struct by value!
    // Return the current position in the current ref sys (RA, Dec for ICRS, FK5, ...)
    def tpkc_currentPositionA(self: Pointer): Double
    def tpkc_currentPositionB(self: Pointer): Double
  }

  /**
   * Gets a new instance of the TpkC C class, using this interface
   */
  def getInstance(): TpkC = {
    val tpkExternC = LibraryLoader.create(classOf[TpkExternC]).load("tpk-jni")
    val self       = tpkExternC.tpkc_ctor()
    new TpkC(tpkExternC, self)
  }
}

class TpkC(val tpkExternC: TpkExternC, val self: Pointer) {
  def init(): Unit = {
    tpkExternC.tpkc_init(self)
  }

  def newDemands(mAz: Double, mEl: Double, eAz: Double, eEl: Double, m3R: Double, m3T: Double): Unit = {
    tpkExternC.tpkc_newDemands(self, mAz, mEl, eAz, eEl, m3R, m3T)
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

  // The mount's current RA position  (if using ICRS, FK5)
  def currentPositionA(): Double = {
    tpkExternC.tpkc_currentPositionA(self)
  }

  // The mount's current Dec position (if using ICRS, FK5)
  def currentPositionB(): Double = {
    tpkExternC.tpkc_currentPositionB(self)
  }
}
