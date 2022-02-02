package tcs

import akka.actor.typed.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.{LoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.Completed
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Angle
import csw.params.core.models.Coords.EqFrame.ICRS
import csw.params.core.models.Coords.{Coord, EqCoord}
import csw.params.events.EventKey
import csw.prefix.models.Prefix
import csw.testkit.scaladsl.CSWService.{AlarmServer, EventServer}
import csw.testkit.scaladsl.ScalaTestFrameworkTestKit
import org.scalatest.funsuite.AnyFunSuiteLike
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import tcs.EventHandler.StopTest

import java.net.InetAddress
import scala.concurrent.Await
import scala.concurrent.duration._

class TcsIntegrationTests extends ScalaTestFrameworkTestKit(AlarmServer, EventServer) with AnyFunSuiteLike {
  import frameworkTestKit._
  private val pkConnection  = AkkaConnection(ComponentId(Prefix("TCS.PointingKernelAssembly"), ComponentType.Assembly))
  private val mcsConnection = AkkaConnection(ComponentId(Prefix("TCS.MCSAssembly"), ComponentType.Assembly))
  private val encConnection = AkkaConnection(ComponentId(Prefix("TCS.ENCAssembly"), ComponentType.Assembly))

  private val basePosKey: Key[Coord]  = KeyType.CoordKey.make("base")
  private val prefix                  = Prefix("TCS.test")
  private val slewToTargetCommandName = CommandName("SlewToTarget")
  private val obsId                   = None
  private val eventKeys = Set(
    "TCS.PointingKernelAssembly.MountDemandPosition",
    "TCS.PointingKernelAssembly.EnclosureDemandPosition",
    "TCS.ENCAssembly.CurrentPosition",
    "TCS.MCSAssembly.MountPosition"
  ).map(EventKey.apply)

  implicit val timeout: Timeout = Timeout(3.minutes)
  LoggingSystemFactory.start("LoggingTestApp", "0.1", InetAddress.getLocalHost.getHostName, actorSystem)
  implicit lazy val log: Logger = new LoggerFactory(prefix).getLogger

  override def beforeAll(): Unit = {
    super.beforeAll()
    spawnContainer(com.typesafe.config.ConfigFactory.load("McsEncPkContainer.conf"))
  }

  test("Assemblies should be locatable using Location Service") {
    log.info("Locating PointingKernelAssembly")
    val pkAkkaLocation = Await.result(locationService.resolve(pkConnection, 10.seconds), 10.seconds).get
    pkAkkaLocation.connection shouldBe pkConnection
    log.info("Located PointingKernelAssembly")

    val mcsAkkaLocation = Await.result(locationService.resolve(mcsConnection, 10.seconds), 10.seconds).get
    mcsAkkaLocation.connection shouldBe mcsConnection
    log.info("Located MCSAssembly")

    val encAkkaLocation = Await.result(locationService.resolve(encConnection, 10.seconds), 10.seconds).get
    encAkkaLocation.connection shouldBe encConnection
    log.info("Located ENCAssembly")
  }

  private def slewToTarget(ra: Angle, dec: Angle): Unit = {
    val pkAkkaLocation = Await.result(locationService.resolve(pkConnection, 10.seconds), 10.seconds).get
    val pkAssembly     = CommandServiceFactory.make(pkAkkaLocation)
    val eqCoord = EqCoord(
      ra = ra,
      dec = dec,
      frame = ICRS,
      //      frame = FK5,
      pmx = 0,
      pmy = 0
    )
    val posParam = basePosKey.set(eqCoord)
    val setup    = Setup(prefix, slewToTargetCommandName, obsId).add(posParam)
    val raDecStr = s"RA=${Angle.raToString(ra.toRadian)}, Dec=${Angle.deToString(dec.toRadian)}"
    log.info(s"SlewToTarget $raDecStr")
    val resp = Await.result(pkAssembly.submitAndWait(setup), timeout.duration)
    if (resp != Completed(resp.runId))
      log.error(s"Received error response from SlewToTarget $raDecStr")
    assert(resp == Completed(resp.runId))
    log.info(s"Matched demand to $raDecStr")
  }

  test("SlewToTarget command to pk assembly should cause MCS and ENC events to show eventual move to target") {
    import Angle._
    implicit val sched: Scheduler = actorSystem.scheduler

    val subscriber        = eventService.defaultSubscriber
    val testActor         = actorSystem.spawn(EventHandler.TestActor.make(), "TestActor")
    val eventHandler      = actorSystem.spawn(EventHandler.make(testActor), "EventHandler")
    val eventSubscription = subscriber.subscribeActorRef(eventKeys, eventHandler)

    slewToTarget(10.arcHour, 30.degree)
    slewToTarget(15.arcHour, 40.degree)
    slewToTarget(5.arcHour, 25.degree)

    eventSubscription.unsubscribe()
    testActor ! StopTest

    actorSystem.terminate()
    System.exit(0)
  }

}
