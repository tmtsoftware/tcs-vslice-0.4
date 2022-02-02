package tcs.shared

import csw.params.core.models.{Angle, Coords}
import csw.params.core.models.Coords.AltAzCoord
import org.scalatest.funsuite.AnyFunSuite
import Angle._

class SimulationUtilTest extends AnyFunSuite {

  val closeEnoughUas = 0.001 * Angle.S2Uas

  // Simulate converging on the target
  private def getNextPos(targetPos: AltAzCoord, currentPos: AltAzCoord): AltAzCoord = {
    // The max slew for az is 2.5 deg/sec.  Max for el is 1.0 deg/sec
    val azSpeed = 2.5 // deg/sec
    val elSpeed = 1.0 // deg/sec
    val rate    = 1.0 // hz
    val factor  = 2.0 // Speedup factor for test/demo
    AltAzCoord(
      targetPos.tag,
      SimulationUtil.move(elSpeed * factor, rate, targetPos.alt, currentPos.alt),
      SimulationUtil.move(azSpeed * factor, rate, targetPos.az, currentPos.az)
    )
  }

  test("Test simulated move from current to demand coords") {
    val current                = AltAzCoord(Coords.Tag("test"), 50.degree, 10.degree)
    val demand                 = AltAzCoord(Coords.Tag("test2"), 15.degree, 25.degree)
    var currentPos: AltAzCoord = current
    do {
      currentPos = getNextPos(demand, currentPos)
      val dist = Angle.distance(currentPos.alt.toRadian, currentPos.az.toRadian, demand.alt.toRadian, demand.az.toRadian)
//      println(s"XXX dist = ${dist * Angle.R2S} arcsec")
    } while (math.abs(currentPos.alt.uas - demand.alt.uas) > closeEnoughUas || math.abs(currentPos.az.uas - demand.az.uas) > closeEnoughUas)
  }
}
