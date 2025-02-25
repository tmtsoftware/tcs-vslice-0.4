package tcs.enc

import csw.location.api.models.Connection.PekkoConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike

import scala.concurrent.Await
import scala.concurrent.duration._

class EncAssemblyTest extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyFunSuiteLike {

  import frameworkTestKit._

  override def beforeAll(): Unit = {
    super.beforeAll()
    spawnStandalone(com.typesafe.config.ConfigFactory.load("enc-assembly-standalone.conf"))
  }

  test("Assembly should be locatable using Location Service") {
    val connection    = PekkoConnection(ComponentId(Prefix("TCS.ENCAssembly"), ComponentType.Assembly))
    val pekkoLocation = Await.result(locationService.resolve(connection, 10.seconds), 10.seconds).get

    pekkoLocation.connection shouldBe connection

    // Give it time to close down to avoid error messages
    Thread.sleep(1000)
  }
}
