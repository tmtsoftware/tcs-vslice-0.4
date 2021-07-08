package tcs.pk.wrapper

import csw.params.core.models.Coords.EqCoord

/**
 * This is a wrapper class for TPK and will act as an endpoint. It helps in
 * calling TPK New Target and Offset methods so that specific demands can be
 * generated by TPK System
 *
 */
class TpkWrapper() {
  System.loadLibrary("tpk-jni")
  private val tpkEndpoint = new TpkC()

  /**
   * This will help registering and Initializing TPK, once this method is
   * invoked TPK will start generation default Demands
   */
  def initiate(): Unit = {
    tpkEndpoint.init()
  }

  /**
   * New target from Ra, Dec position. Target applies to Mount and
   * Enclosure
   */
  def newTarget(pos: EqCoord): Unit = {
    tpkEndpoint.newTarget(pos.ra.toDegree, pos.dec.toDegree)
  }
}
