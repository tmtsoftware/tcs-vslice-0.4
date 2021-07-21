package tcs.pk.wrapper

import jnr.ffi.Pointer
import jnr.ffi.LibraryLoader
import TpkC._

object TpkC {

  /**
   * Matching interface for the extern "C" API defined in TpkC.cpp in the tpk-jni subproject
   */
  trait TpkExternC {
    def tpkc_ctor(): Pointer

    def tpkc_init(self: Pointer): Unit

    def tpkc_newDemands(self: Pointer, mAz: Double, mEl: Double, eAz: Double, eEl: Double, m3R: Double, m3T: Double): Unit

    def tpkc_newTarget(self: Pointer, ra: Double, dec: Double): Unit

    def tpkc_offset(self: Pointer, raO: Double, decO: Double): Unit
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

  def newTarget(ra: Double, dec: Double): Unit = {
    tpkExternC.tpkc_newTarget(self, ra, dec)
  }

  def offset(raO: Double, decO: Double): Unit = {
    tpkExternC.tpkc_offset(self, raO, decO)
  }
}
