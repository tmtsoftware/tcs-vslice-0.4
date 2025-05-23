package tcs

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Scheduler, SpawnProtocol}
import org.apache.pekko.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventService
import csw.event.client.EventServiceFactory
import csw.location.api.models.Connection.PekkoConnection
import csw.location.api.models.{ComponentId, ComponentType}
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
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
import csw.logging.client.commons.PekkoTypedExtension.UserActorFactory

import java.net.InetAddress
import scala.concurrent.Await
import scala.concurrent.duration._
import Angle._
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import tcs.EventHandler.StopTest

// This is a standalone app that expects csw-services to be already running!
object TcsIntegrationTestApp {
  def main(args: Array[String]): Unit = {
    if (Option(System.getenv("TPK_USE_FAKE_SYSTEM_CLOCK")).isEmpty) {
      println(s"Please set the environment variable TPK_USE_FAKE_SYSTEM_CLOCK to 1 before running the TcsIntegrationTestApp")
      System.exit(1)
    }

    val pkConnection = PekkoConnection(ComponentId(Prefix("TCS.PointingKernelAssembly"), ComponentType.Assembly))

    val basePosKey: Key[Coord]  = KeyType.CoordKey.make("base")
    val prefix                  = Prefix("TCS.test")
    val slewToTargetCommandName = CommandName("SlewToTarget")
    val obsId                   = None
    val eventKeys = Set(
      "TCS.ENCAssembly.CurrentPosition",
      "TCS.MCSAssembly.MountPosition"
    ).map(EventKey.apply)

    implicit val actorSystem: ActorSystem[SpawnProtocol.Command] =
      ActorSystemFactory.remote(SpawnProtocol(), "TcsIntegrationTestApp")
    implicit val sched: Scheduler = actorSystem.scheduler
    implicit val timeout: Timeout = Timeout(3.minutes)
    val loggingSystem =
      LoggingSystemFactory.start("TcsIntegrationTestApp", "0.1", InetAddress.getLocalHost.getHostName, actorSystem)
    implicit lazy val log: Logger = new LoggerFactory(prefix).getLogger
    val locationService           = HttpLocationServiceFactory.makeLocalClient(actorSystem)
    lazy val eventService: EventService = {
      new EventServiceFactory().make(locationService)(actorSystem)
    }
    val subscriber        = eventService.defaultSubscriber
    val testActor         = actorSystem.spawn(EventHandler.TestActor.make(), "TestActor")
    val eventHandler      = actorSystem.spawn(EventHandler.make(testActor), "EventHandler")
    val eventSubscription = subscriber.subscribeActorRef(eventKeys, eventHandler)

    def slewToTarget(ra: Angle, dec: Angle, testActor: ActorRef[EventHandler.TestActorMessages]): Unit = {
      val pkPekkoLocation = Await.result(locationService.resolve(pkConnection, 10.seconds), 10.seconds).get
      val pkAssembly      = CommandServiceFactory.make(pkPekkoLocation)
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
      val resp     = Await.result(pkAssembly.submitAndWait(setup), timeout.duration)
      if (resp != Completed(resp.runId))
        log.error(s"Received error response from SlewToTarget $raDecStr")
      assert(resp == Completed(resp.runId))
      Thread.sleep(1000) // Wait for new demand
      val t1 = System.currentTimeMillis()
      assert(Await.result(testActor ? EventHandler.MatchDemand.apply, timeout.duration))
      val t2   = System.currentTimeMillis()
      val secs = (t2 - t1) / 1000.0
      log.info(s"Matched demand to $raDecStr (time: $secs secs)")
    }

    slewToTarget(10.arcHour, 30.degree, testActor)
    slewToTarget(15.arcHour, 40.degree, testActor)
    slewToTarget(12.arcHour, 65.degree, testActor)

    //  loggingSystem.stop
    eventSubscription.unsubscribe()
    testActor ! StopTest

    actorSystem.terminate()
    System.exit(0)
  }
}
