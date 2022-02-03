package tcs.shared

import csw.params.core.models.Angle
import csw.params.core.models.Angle.double2angle

object SimulationUtil {

  /**
   * Simulate moving from current to target position
   * @param speed rotation speed in degrees / second (when not close to target)
   * @param rate the rate in hz that this function is called
   * @param demand demand coord
   * @param current current coord
   * @return the next position
   */
  def move(speed: Double, rate: Int, demand: Angle, current: Angle): Angle = {
    // XXX TODO FIXME
    val diff = demand.uas - current.uas
    Angle(current.uas + diff / 2)
  }
}
