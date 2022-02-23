package tcs.mcs

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.command.client.messages.TopLevelActorMessage
import csw.event.api.scaladsl.SubscriptionModes.RateLimiterMode
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.ControlCommand
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.{AltAzCoord, EqCoord}
import csw.params.core.models.{Id, Units}
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS
import csw.time.core.models.UTCTime
import tcs.shared.SimulationUtil

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Tcshcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */

object McsAssemblyHandlers {
  private val pkAssemblyPrefix         = Prefix(TCS, "PointingKernelAssembly")
  private val pkMountDemandPosEventKey = EventKey(pkAssemblyPrefix, EventName("MountDemandPosition"))
  private val pkDemandPosKey           = KeyType.AltAzCoordKey.make("pos")
  private val pkRaDecDemandPosKey      = KeyType.EqCoordKey.make("posRaDec")
  private val pkSiderealTimeKey        = KeyType.DoubleKey.make("siderealTime", Units.hour)
  private val pkEventKeys              = Set(pkMountDemandPosEventKey)

  // Actor to receive Assembly events
  private object EventHandlerActor {
    private val currentPosKey: Key[AltAzCoord]   = KeyType.AltAzCoordKey.make("current")
    private val demandPosKey: Key[AltAzCoord]    = KeyType.AltAzCoordKey.make("demand")
    private val currentPosRaDecKey: Key[EqCoord] = KeyType.EqCoordKey.make("currentPos")
    private val demandPosRaDecKey: Key[EqCoord]  = KeyType.EqCoordKey.make("demandPos")
    private val currentHourAngleKey              = KeyType.DoubleKey.make("currentHourAngle", Units.degree)
    private val demandHourAngleKey               = KeyType.DoubleKey.make("demandHourAngle", Units.degree)
    private val mcsTelPosEventName               = EventName("MountPosition")

    def make(cswCtx: CswContext): Behavior[Event] = {
      Behaviors.setup(ctx => new EventHandlerActor(ctx, cswCtx))
    }
  }

  private class EventHandlerActor(ctx: ActorContext[Event], cswCtx: CswContext) extends AbstractBehavior[Event](ctx) {

    import EventHandlerActor._
    import cswCtx._

    private val log                                   = loggerFactory.getLogger
    private val publisher                             = cswCtx.eventService.defaultPublisher
    private var maybeCurrentPosRaDec: Option[EqCoord] = None

    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent if e.eventKey == pkMountDemandPosEventKey && e.paramSet.nonEmpty =>
          // Note from doc: Mount accepts demands at 100Hz a nd enclosure accepts demands at 20Hz
          // Assuming we are receiving MountDemandPosition events at 100hz, we want to publish at 1hz.
          try {
            val altAzCoordDemand  = e(pkDemandPosKey).head
            val raDecCoordDemand  = e(pkRaDecDemandPosKey).head
            val siderealTimeHours = e(pkSiderealTimeKey).head

            maybeCurrentPosRaDec match {
              case Some(currentPos) =>
                val newRaDecPos = getNextPos(raDecCoordDemand, currentPos)
                val newAltAzPos = CoordUtil.raDecToAltAz(siderealTimeHours, newRaDecPos)
                val newEvent = SystemEvent(cswCtx.componentInfo.prefix, mcsTelPosEventName)
                  .madd(
                    currentPosKey.set(newAltAzPos),
                    demandPosKey.set(altAzCoordDemand),
                    currentPosRaDecKey.set(newRaDecPos),
                    demandPosRaDecKey.set(raDecCoordDemand),
                    pkSiderealTimeKey.set(siderealTimeHours),
                    currentHourAngleKey.set(siderealTimeHours * 15 - newRaDecPos.ra.toDegree),
                    demandHourAngleKey.set(siderealTimeHours * 15 - raDecCoordDemand.ra.toDegree)
                  )
                publisher.publish(newEvent)
                maybeCurrentPosRaDec = Some(newRaDecPos)
              case None =>
                maybeCurrentPosRaDec = Some(raDecCoordDemand)
            }
          }
          catch {
            case e: Exception =>
              log.error(e.getMessage, ex = e)
          }
        case x =>
          if (!x.isInvalid)
            log.error(s"Unexpected event received: $x")
      }

      Behaviors.same
    }

    // Simulate converging on the demand target
    private def getNextPos(demandPos: EqCoord, currentPos: EqCoord): EqCoord = {
      val speed  = 12  // deg/sec
      val rate   = 1   // hz
      val factor = 1.0 // Speedup factor for test/demo
      EqCoord(
        demandPos.tag,
        SimulationUtil.move(speed * factor, rate, demandPos.ra, currentPos.ra),
        SimulationUtil.move(speed * factor, rate, demandPos.dec, currentPos.dec),
        demandPos.frame,
        demandPos.catalogName,
        demandPos.pm
      )
    }
  }
}

class McsAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import McsAssemblyHandlers._
  import cswCtx._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  log.info("Initializing MCS assembly...")
  private val subscriber        = cswCtx.eventService.defaultSubscriber
  private val eventHandler      = ctx.spawn(EventHandlerActor.make(cswCtx), "McsAssemblyEventHandler")
  private val eventSubscription = subscriber.subscribeActorRef(pkEventKeys, eventHandler, 1.second, RateLimiterMode)

  override def initialize(): Unit = {}

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = Completed(runId)

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {
    Await.ready(eventSubscription.unsubscribe(), 1.second)
    ctx.stop(eventHandler)
  }

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
