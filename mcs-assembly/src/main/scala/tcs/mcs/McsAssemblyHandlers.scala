package tcs.mcs

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.ControlCommand
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Angle.double2angle
import csw.params.core.models.Coords.AltAzCoord
import csw.params.core.models.{Angle, Id}
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS
import csw.time.core.models.UTCTime

import scala.concurrent.ExecutionContextExecutor

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Tcshcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */

object McsAssemblyHandlers {
  private val mcsAssemblyPrefix        = Prefix(TCS, "MCS_Assembly")
  private val pkAssemblyPrefix         = Prefix(TCS, "PointingKernelAssembly")
  private val pkMountDemandPosEventKey = EventKey(pkAssemblyPrefix, EventName("MountDemandPosition"))
  private val pkEventKeys              = Set(pkMountDemandPosEventKey)

  // Actor to receive Assembly events
  private object EventHandlerActor {
    private val posKey: Key[AltAzCoord] = KeyType.AltAzCoordKey.make("pos")
    private val mcsTelPosEventName      = EventName("TCS Telescope Position")

    def make(cswCtx: CswContext): Behavior[Event] = {
      Behaviors.setup(ctx => new EventHandlerActor(ctx, cswCtx))
    }
  }

  private class EventHandlerActor(ctx: ActorContext[Event], cswCtx: CswContext, maybeCurrentPos: Option[AltAzCoord] = None)
      extends AbstractBehavior[Event](ctx) {
    import EventHandlerActor._
    import cswCtx._
    private val log = loggerFactory.getLogger
    // Assuming we are receiving MountDemandPosition events at 20hz, we want to publish at 1hz
    private val publisher = cswCtx.eventService.defaultPublisher
    // XXX move at this rate
    private var count = 0

    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent =>
          if (e.eventKey == pkMountDemandPosEventKey) {
            count = (count + 1) % 100
            if (count == 0) {
              val altAzCoordDemand = e(posKey).head
              maybeCurrentPos match {
                case Some(currentPos) =>
                  val newPos = getNextPos(altAzCoordDemand, currentPos)
                  val newEvent = SystemEvent(mcsAssemblyPrefix, mcsTelPosEventName)
                    .add(posKey.set(newPos))
                  publisher.publish(newEvent)
                  new EventHandlerActor(ctx, cswCtx, Some(newPos))
                case None =>
                  new EventHandlerActor(ctx, cswCtx, Some(altAzCoordDemand))
              }
            }
            else Behaviors.same
          }
          else Behaviors.same
        case x =>
          log.error(s"Expected SystemEvent but got $x")
          Behaviors.same
      }
    }

    // Simulate moving towards target
    private def move(target: Angle, current: Angle): Angle = {
      val rotSpeed = 10.0 // deg/sec
      val factor   = 0.5
      val limit    = rotSpeed + factor
      val diff     = (target - current).toDegree
      val d        = Math.abs(diff)
      val sign     = Math.signum(diff)
      if (d > limit)
        current + (rotSpeed * sign).degree
      else
        current + (diff * factor).degree
    }

    // Simulate converging on the target
    private def getNextPos(targetPos: AltAzCoord, currentPos: AltAzCoord): AltAzCoord = {
      AltAzCoord(targetPos.tag,
        move(targetPos.alt, currentPos.alt),
        move(targetPos.az, currentPos.az))
    }
  }

}

class McsAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {
  import McsAssemblyHandlers._
  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  override def initialize(): Unit = {
    log.info("Initializing MCS assembly...")
    val subscriber   = cswCtx.eventService.defaultSubscriber
    val eventHandler = ctx.spawn(EventHandlerActor.make(cswCtx), "McsAssemblyEventHandler")
    subscriber.subscribeActorRef(pkEventKeys, eventHandler)
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = Completed(runId)

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
