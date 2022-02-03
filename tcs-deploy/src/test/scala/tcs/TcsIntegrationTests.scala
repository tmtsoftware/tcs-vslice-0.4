package tcs

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, Scheduler}
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.{ContainerMessage, SupervisorContainerCommonMessages}
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
    "TCS.ENCAssembly.CurrentPosition",
    "TCS.MCSAssembly.MountPosition"
  ).map(EventKey.apply)

  implicit val sched: Scheduler = actorSystem.scheduler
  implicit val timeout: Timeout = Timeout(3.minutes)
  LoggingSystemFactory.start("LoggingTestApp", "0.1", InetAddress.getLocalHost.getHostName, actorSystem)
  implicit lazy val log: Logger             = new LoggerFactory(prefix).getLogger
  var container: ActorRef[ContainerMessage] = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    container = spawnContainer(com.typesafe.config.ConfigFactory.load("McsEncPkContainer.conf"))
  }

  override protected def afterAll(): Unit = {
    container ! SupervisorContainerCommonMessages.Shutdown
    super.afterAll()
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

  private def slewToTarget(ra: Angle, dec: Angle, testActor: ActorRef[EventHandler.TestActorMessages]): Unit = {
    val pkAkkaLocation = Await.result(locationService.resolve(pkConnection, 10.seconds), 10.seconds).get
    val pkAssembly     = CommandServiceFactory.make(pkAkkaLocation)
    val eqCoord = EqCoord(
      ra = ra,
      dec = dec,
      frame = ICRS,
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
    Thread.sleep(1000) // Wait for new demand
    val t1 = System.currentTimeMillis()
    assert(Await.result(testActor ? EventHandler.MatchDemand, timeout.duration))
    val t2   = System.currentTimeMillis()
    val secs = (t2 - t1) / 1000.0
    log.info(s"Matched demand to $raDecStr (time: $secs secs)")
  }

  test("SlewToTarget command to pk assembly should cause MCS and ENC events to show eventual move to target") {
    import Angle._
    implicit val sched: Scheduler = actorSystem.scheduler

    val subscriber        = eventService.defaultSubscriber
    val testActor         = actorSystem.spawn(EventHandler.TestActor.make(), "TestActor")
    val eventHandler      = actorSystem.spawn(EventHandler.make(testActor), "EventHandler")
    val eventSubscription = subscriber.subscribeActorRef(eventKeys, eventHandler)

    slewToTarget(10.arcHour, 30.degree, testActor)
    slewToTarget(15.arcHour, 40.degree, testActor)
    slewToTarget(5.arcHour, 25.degree, testActor)

    Await.ready(eventSubscription.unsubscribe(), timeout.duration)
    testActor ! StopTest
  }

}
