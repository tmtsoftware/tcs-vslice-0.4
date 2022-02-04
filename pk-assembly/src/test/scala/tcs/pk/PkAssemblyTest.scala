package tcs.pk

import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.params.commands.CommandIssue.ParameterValueOutOfRangeIssue
import csw.params.commands.CommandResponse.Invalid
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Angle
import csw.params.core.models.Coords.{Coord, EqCoord}
import csw.params.core.models.Coords.EqFrame.FK5
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

class PkAssemblyTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._
  private val connection              = AkkaConnection(ComponentId(Prefix("TCS.PointingKernelAssembly"), ComponentType.Assembly))
  private val basePosKey: Key[Coord]  = KeyType.CoordKey.make("base")
  private val prefix                  = Prefix("TCS.pk_client")
  private val slewToTargetCommandName = CommandName("SlewToTarget")
  private val obsId                   = None
  implicit val timeout: Timeout       = Timeout(10.seconds)

  override def beforeAll(): Unit = {
    super.beforeAll()
    spawnContainer(com.typesafe.config.ConfigFactory.load("PkContainer.conf"))
  }

  test("Assembly should be locatable using Location Service") {
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get
    akkaLocation.connection shouldBe connection
  }

  test("Assembly should validate RA coordinate in Setup") {
    import Angle._
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get
    val assembly     = CommandServiceFactory.make(akkaLocation)
    val eqCoord = EqCoord(
      ra = 120.arcHour,
      dec = 20.degree,
      frame = FK5,
      pmx = 0,
      pmy = 0
    )
    val posParam = basePosKey.set(eqCoord)
    val setup    = Setup(prefix, slewToTargetCommandName, obsId).add(posParam)
    val resp     = Await.result(assembly.submitAndWait(setup), timeout.duration)
    resp shouldBe (Invalid(resp.runId, ParameterValueOutOfRangeIssue(s"RA value out of range: 120.0 hours")))
  }

  test("Assembly should validate Dec coordinate in Setup") {
    import Angle._
    val akkaLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get
    val assembly     = CommandServiceFactory.make(akkaLocation)
    val eqCoord = EqCoord(
      ra = 12.arcHour,
      dec = 200.degree,
      frame = FK5,
      pmx = 0,
      pmy = 0
    )
    val posParam = basePosKey.set(eqCoord)
    val setup    = Setup(prefix, slewToTargetCommandName, obsId).add(posParam)
    val resp     = Await.result(assembly.submitAndWait(setup), timeout.duration)
    resp shouldBe (Invalid(resp.runId, ParameterValueOutOfRangeIssue(s"Dec value out of range: 200.0 deg")))
  }
}
