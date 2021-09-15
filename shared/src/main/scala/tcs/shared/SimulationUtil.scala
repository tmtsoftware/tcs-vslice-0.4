package tcs.shared

import csw.params.core.models.Angle
import csw.params.core.models.Angle.double2angle

object SimulationUtil {

  /**
   * Simulate moving from current to target position
   * @param speed rotation speed in degrees / second (when not close to target)
   * @param rate the rate in hz that this function is called
   * @param target target coord
   * @param current current coord
   * @return the next position
   */
  def move(speed: Double, rate: Double, target: Angle, current: Angle): Angle = {
    val factor = 0.5 // slow down this much when near target
    val limit  = speed / rate + factor
    val diff   = (target - current).toDegree
    val d      = Math.abs(diff)
    val sign   = Math.signum(diff)
    if (d > limit)
      current + (speed / rate * sign).degree
    else
      current + (diff * factor).degree
  }
}
