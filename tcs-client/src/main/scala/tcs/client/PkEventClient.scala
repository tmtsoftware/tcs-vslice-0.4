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
import csw.params.commands.Setup
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.ProperMotion
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import csw.params.core.models.Coords.EqCoord
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS
import csw.services.BuildInfo

import scala.concurrent.duration._
import scala.util.{Failure, Success}

//noinspection DuplicatedCode,SameParameterValue
// A client to test locating and communicating with the Test assembly
object PkEventClient extends App {

  Options.parse(args, run)

  // Run the application
  private def run(options: Options): Unit = {
    val host                                            = InetAddress.getLocalHost.getHostName
    val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "PkEventClient")
    import typedSystem.executionContext

    LoggingSystemFactory.start("PkEventClient", BuildInfo.version, host, typedSystem)
    val log = GenericLoggerFactory.getLogger
    log.info("Starting PkEventClient")

    val locationService           = HttpLocationServiceFactory.makeLocalClient(typedSystem)
    implicit val timeout: Timeout = Timeout(10.seconds)
    val obsId                     = options.obsId
    val posKey: Key[EqCoord]      = KeyType.EqCoordKey.make("pos")
    val prefix                    = Prefix("TCS.pk_event_client")
    val commandName               = options.command

    def makeSetup(): Setup = {
      val pm = ProperMotion(options.pmx, options.pmy)
      val eqCoord = EqCoord(
        ra = options.ra,
        dec = options.dec,
        frame = options.frame,
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

    class EventHandler(ctx: ActorContext[Event]) extends AbstractBehavior[Event](ctx) {
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
            interact(CommandServiceFactory.make(loc.asInstanceOf[AkkaLocation])(ctx.system))
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

    def interact(assembly: CommandService): Unit = {
      log.info(s"Sending filter0 setup to assembly")
      assembly.submitAndWait(makeSetup()).onComplete {
        case Success(response) => log.info(s"Submit succeeded = $response")
        case Failure(ex)       => log.info(s"Submit failed: $ex")
      }
    }
  }
}
