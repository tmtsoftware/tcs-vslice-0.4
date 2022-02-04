package tcs.mcs

import org.scalatest.funsuite.AnyFunSuite
import csw.params.core.models.Angle._
import csw.params.core.models.Coords.{EqCoord, Tag}
import csw.params.core.models.Coords.EqFrame.ICRS
import csw.params.core.models.ProperMotion

class CoordUtilTests extends AnyFunSuite {
  val st  = 23.3
  val tag = Tag("BASE")
  val cat = "none"
  val pm  = ProperMotion(0, 0)

  test("Convert RA, Dec to AltAz") {
    val eqCoord     = EqCoord(tag, 20.arcHour, 15.degree, ICRS, cat, pm)
    val altAzCoord1 = CoordUtil.raDecToAltAz(st, eqCoord)
    val eqCoord1    = CoordUtil.altAzToRaDec(st, altAzCoord1)
    println(s"XXX altAzCoord1 = $altAzCoord1")
    println(s"XXX eqCoord1 = $eqCoord1")
    assert(eqCoord1.toString() == eqCoord.toString())
  }
}
