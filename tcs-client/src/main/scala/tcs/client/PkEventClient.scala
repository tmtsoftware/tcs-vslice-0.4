package tcs.client

import java.net.InetAddress
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventService
import csw.event.client.EventServiceFactory
import csw.location.api.models.{AkkaLocation, ComponentId, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.{ObsId, ProperMotion}
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import csw.params.core.models.Coords.EqCoord
import csw.params.core.models.Coords.EqFrame.FK5
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS

import scala.concurrent.duration._
import scala.util.{Failure, Success}

//noinspection DuplicatedCode,SameParameterValue
// A client to test locating and communicating with the Test assembly
object PkEventClient extends App {

  private val host                                    = InetAddress.getLocalHost.getHostName
  val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "PkEventClient")
  import typedSystem.executionContext

  LoggingSystemFactory.start("PkEventClient", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting PkEventClient")

  val locationService              = HttpLocationServiceFactory.makeLocalClient(typedSystem)
  implicit val timeout: Timeout    = Timeout(10.seconds)
  private val obsId                = ObsId("2021A-001-123")
  private val posKey: Key[EqCoord] = KeyType.EqCoordKey.make("pos")
  private val prefix               = Prefix("TCS.pk-event-client")
  private val commandName          = CommandName("SlewToTarget")

  // TODO: specify command/coords via command line args
  private def makeSetup(): Setup = {
    val pm = ProperMotion(0.5, 2.33)
    val eqCoord = EqCoord(
      ra = "12:13:14.15",
      dec = "-30:31:32.3",
      frame = FK5,
      pmx = pm.pmx,
      pmy = pm.pmy
    )
    val posParam = posKey.set(
      eqCoord
    )
    Setup(prefix, commandName, Some(obsId)).add(posParam)
  }

  val pkAssemblyPrefix = Prefix(TCS, "PointingKernelAssembly")
  val connection       = AkkaConnection(ComponentId(pkAssemblyPrefix, Assembly))

  lazy val eventService: EventService = {
    new EventServiceFactory().make(locationService)(typedSystem)
  }

  // Actor to receive Assembly events
  object EventHandler {
    def make(): Behavior[Event] = {
      log.info("Starting event handler")
      Behaviors.setup(ctx => new EventHandler(ctx))
    }
  }

  private class EventHandler(ctx: ActorContext[Event]) extends AbstractBehavior[Event](ctx) {
    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent =>
          log.info(s"Received event: $e")
        case x =>
          log.error(s"Expected SystemEvent but got $x")
      }
      Behaviors.same
    }
  }

  def startSubscribingToEvents(ctx: ActorContext[TrackingEvent]): Unit = {
    val subscriber   = eventService.defaultSubscriber
    val eventHandler = ctx.spawnAnonymous(EventHandler.make())
    val eventKeys = Set(
      EventKey(pkAssemblyPrefix, EventName("MountDemandPosition")),
      EventKey(pkAssemblyPrefix, EventName("M3DemandPosition")),
      EventKey(pkAssemblyPrefix, EventName("EnclosureDemandPosition"))
    )
    subscriber.subscribeActorRef(eventKeys, eventHandler)
  }

  typedSystem.spawn(initialBehavior, "TestAssemblyClient")

  def initialBehavior: Behavior[TrackingEvent] =
    Behaviors.setup { ctx =>
      locationService.subscribe(
        connection,
        { loc =>
          ctx.self ! loc
        }
      )
      startSubscribingToEvents(ctx)
      subscriberBehavior()
    }

  def subscriberBehavior(): Behavior[TrackingEvent] = {
    Behaviors.receive[TrackingEvent] { (ctx, msg) =>
      msg match {
        case LocationUpdated(loc) =>
          log.info(s"LocationUpdated: $loc")
          interact(ctx, CommandServiceFactory.make(loc.asInstanceOf[AkkaLocation])(ctx.system))
        case LocationRemoved(loc) =>
          log.info(s"LocationRemoved: $loc")
      }
      Behaviors.same
    }
    //    receiveSignal {
    //      case (ctx, x) =>
    //        log.info(s"${ctx.self} received signal $x")
    //        Behaviors.stopped
    //    }
  }

  private def interact(ctx: ActorContext[TrackingEvent], assembly: CommandService): Unit = {
    log.info(s"Sending filter0 setup to assembly")
    assembly.submitAndWait(makeSetup()).onComplete {
      case Success(response) => log.info(s"Submit succeeded = $response")
      case Failure(ex)       => log.info(s"Submit failed: $ex")
    }
  }
}
