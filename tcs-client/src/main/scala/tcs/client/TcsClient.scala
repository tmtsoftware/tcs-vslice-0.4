package tcs.client

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}

import java.net.InetAddress
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventService
import csw.event.api.scaladsl.SubscriptionModes.RateLimiterMode
import csw.event.client.EventServiceFactory
import csw.location.api.models.ComponentId
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.CommandResponse.isNegative
import csw.params.commands.Setup
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqFrame.ICRS
import csw.params.core.models.Coords.{Coord, EqCoord, EqFrame}
import csw.params.core.models.ProperMotion
import csw.params.events.{Event, EventKey, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory

import scala.concurrent.Await
import scala.concurrent.duration._

//noinspection DuplicatedCode
// A client to test locating the pk assembly and sending it a command
object TcsClient extends App {
  val basePosKey: Key[Coord] = KeyType.CoordKey.make("base")

  // Keys for telescope offsets in arcsec
  private val xCoordinate: Key[Double] = KeyType.DoubleKey.make("Xcoordinate")
  private val yCoordinate: Key[Double] = KeyType.DoubleKey.make("Ycoordinate")

  implicit val timeout: Timeout = Timeout(10.seconds)
  val prefix                    = Prefix("TCS.pk_client")
  // TODO: Pass the assembly name as an option?
  val pkAssemblyPrefix  = Prefix(TCS, "PointingKernelAssembly")
  val encAssemblyPrefix = Prefix(TCS, "ENCAssembly")
  val mcsAssemblyPrefix = Prefix(TCS, "MCSAssembly")
  val connection        = AkkaConnection(ComponentId(pkAssemblyPrefix, Assembly))

  Options.parse(args, run)

  // Run the application
  private def run(options: Options): Unit = {
    def error(s: String): Unit = {
      println(s"Error: $s")
      System.exit(1)
    }
    if (options.convertRaDec) {
      import csw.params.core.models.Angle
      if (options.ra.isEmpty)
        error("Missing --ra option")
      if (options.dec.isEmpty)
        error("Missing --dec option")
      val ra  = Angle.raToString(Angle(options.ra.get.toLong).toRadian)
      val dec = Angle.deToString(Angle(options.dec.get.toLong).toRadian)
      println(s"$ra $dec")
    }
    else {
      val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "TestAssemblyClientSystem")

      val host = InetAddress.getLocalHost.getHostName
      LoggingSystemFactory.start("TcsClient", "0.1", host, typedSystem)
      val log = GenericLoggerFactory.getLogger
      log.info("Starting TcsClient")

      val locationService = HttpLocationServiceFactory.makeLocalClient(typedSystem)
      val obsId           = options.obsId
      val commandName     = options.command

      def makeSetup(): Option[Setup] = {
        commandName.name match {
          case "SlewToTarget" =>
            if (options.ra.nonEmpty && options.dec.nonEmpty) {
              val pm = ProperMotion(options.pmx, options.pmy)
              val eqCoord = EqCoord(
                ra = options.ra.get,
                dec = options.dec.get,
                frame = options.frame.map(EqFrame.withName).getOrElse(ICRS),
                pmx = pm.pmx,
                pmy = pm.pmy
              )
              val posParam = basePosKey.set(
                eqCoord
              )
              Some(Setup(prefix, commandName, obsId).add(posParam))
            }
            else throw new RuntimeException("Missing ra,dec options")
          case "SetOffset" =>
            val x = xCoordinate.set(options.x)
            val y = yCoordinate.set(options.y)
            Some(Setup(prefix, commandName, obsId).add(x).add(y))

          case _ => None
        }
      }

      def startSubscribingToEvents(): Unit = {
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

        lazy val eventService: EventService = new EventServiceFactory().make(locationService)(typedSystem)
        val subscriber                      = eventService.defaultSubscriber
        val eventHandler                    = typedSystem.spawn(EventHandler.make(), "EventHandler")
        // TODO: Use wildcard option for events?
        val eventKeys =
          if (options.events.nonEmpty)
            options.events.map(EventKey.apply).toSet
          else Options.defaultEventKeys.map(EventKey.apply)

        if (options.rateLimiter.nonEmpty)
          subscriber.subscribeActorRef(eventKeys, eventHandler, options.rateLimiter.get.millisecond, RateLimiterMode)
        else
          subscriber.subscribeActorRef(eventKeys, eventHandler)
      }

      Await.result(locationService.resolve(connection, timeout.duration), timeout.duration) match {
        case None =>
          log.error(s"Assembly connection not found: $connection")
        case Some(loc) =>
          val assembly = CommandServiceFactory.make(loc)(typedSystem)
          makeSetup().foreach { setup =>
            val result = Await.result(assembly.submitAndWait(setup), timeout.duration)
            if (isNegative(result)) log.error(s"$result")
            else log.info(s"$result")
          }
      }
      if (options.subscribeToEvents)
        startSubscribingToEvents()
      else
        typedSystem.terminate()
    }
  }
}
