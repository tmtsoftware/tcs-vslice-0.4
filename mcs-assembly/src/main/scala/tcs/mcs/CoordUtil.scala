package tcs.mcs

import csw.params.core.models.Angle
import csw.params.core.models.Coords.{AltAzCoord, EqCoord}

object CoordUtil {

  /**
   *  Convert ra,dec to az,el using the given sidereal time and site latitude.
   * Algorithm based on http://www.stargazing.net/mas/al_az.htm.
   * Note: The native TPK C++ code can do this, but this assembly does not use it (The pk assembly does).
   *
   * @param st sidereal time in hours
   * @param pos the ra,dec coordinates
   * @param latDeg site's latitude in deg (default: for Hawaii)
   * @return the ra.dec coords
   */
  def raDecToAltAz(st: Double, pos: EqCoord, latDeg: Double = 19.82900194): AltAzCoord = {
    import math._
    import csw.params.core.models.Angle._
    val raH  = pos.ra.toDegree / 15.0
    val decD = pos.dec.toDegree
    val s1   = st - raH
    val s2   = if (s1 < 0) s1 + 24 else s1
    val s    = s2 * 15
    val dec  = decD * Angle.D2R
    val lat  = latDeg * Angle.D2R
    val j    = sin(dec) * sin(lat) + cos(dec) * cos(lat) * cos(s)
    val al   = asin(j)
    val j2   = (sin(dec) - sin(lat) * sin(al)) / (cos(lat) * cos(al))
    val az   = acos(j2)
    val j3   = sin(s)
    val az2  = if (j3 > 0) 360 - az * Angle.R2D else az * Angle.R2D
    AltAzCoord(pos.tag, al.radian, az2.degree)
  }

  /**
   *  Convert az,el to ra,dec using the given sidereal time and site latitude.
   * Algorithm based on http://www.stargazing.net/mas/al_az.htm.
   * Note: The native TPK C++ code can do this, but this assembly does not use it (The pk assembly does).
   *
   * @param st sidereal time in hours
   * @param pos the alt/az coordinates
   * @param latDeg site's latitude in deg (default: for Hawaii)
   * @return the ra.dec coords
   */
  def altAzToRaDec(st: Double, pos: AltAzCoord, latDeg: Double = 19.82900194): EqCoord = {
    import Math._
    import csw.params.core.models.Angle._
    val lat = latDeg.degree.toRadian
    val alt = pos.alt.toRadian
    val az  = pos.az.toRadian
    val dec = asin(sin(alt) * sin(lat) + cos(alt) * cos(lat) * cos(az)).radian
    val s   = acos((sin(alt) - sin(lat) * sin(dec.toRadian)) / (cos(lat) * cos(dec.toRadian))).radian
    val s2  = if (sin(az) > 0) 360 - s.toDegree else s.toDegree
    val ra1 = st - s2 / 15
    val ra  = (if (ra1 < 0) ra1 + 24 else ra1).arcHour
    EqCoord(ra, dec, tag = pos.tag)
  }
}
