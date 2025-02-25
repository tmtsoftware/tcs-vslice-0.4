package tcs.enc

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse._
import csw.params.commands.ControlCommand
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Id
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS
import csw.time.core.models.UTCTime
import tcs.shared.SimulationUtil
import csw.params.core.models.Angle.double2angle

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to Tcshcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw/commons/framework.html
 */

object EncAssemblyHandlers {
  private val pkAssemblyPrefix = Prefix(TCS, "PointingKernelAssembly")

  // This assembly subscribes to the pk assembly's EnclosureDemandPosition event
  private val pkEnclosureDemandPosEventKey = EventKey(pkAssemblyPrefix, EventName("EnclosureDemandPosition"))
  private val pkBaseDemandKey: Key[Double] = KeyType.DoubleKey.make("BasePosition")
  private val pkCapDemandKey: Key[Double]  = KeyType.DoubleKey.make("CapPosition")
  private val pkEventKeys                  = Set(pkEnclosureDemandPosEventKey)

  // Actor to receive Assembly events
  private object EventHandlerActor {

    // This assembly fires the CurrentPosition event
    private val encTelPosEventName          = EventName("CurrentPosition")
    private val baseCurrentKey: Key[Double] = KeyType.DoubleKey.make("baseCurrent")
    private val capCurrentKey: Key[Double]  = KeyType.DoubleKey.make("capCurrent")
    private val baseDemandKey: Key[Double]  = KeyType.DoubleKey.make("baseDemand")
    private val capDemandKey: Key[Double]   = KeyType.DoubleKey.make("capDemand")

    def make(cswCtx: CswContext): Behavior[Event] = {
      Behaviors.setup(ctx => new EventHandlerActor(ctx, cswCtx))
    }
  }

  private class EventHandlerActor(
      ctx: ActorContext[Event],
      cswCtx: CswContext,
      maybeCurrentBase: Option[Double] = None,
      maybeCurrentCap: Option[Double] = None
  ) extends AbstractBehavior[Event](ctx) {
    import EventHandlerActor._
    import cswCtx._
    private val log       = loggerFactory.getLogger
    private val publisher = cswCtx.eventService.defaultPublisher

    override def onMessage(msg: Event): Behavior[Event] = {
//      log.info(s"XXX received $msg")
      msg match {
        case e: SystemEvent =>
          if (e.eventKey == pkEnclosureDemandPosEventKey && e.contains(pkBaseDemandKey)) {
            val demandBase = e(pkBaseDemandKey).head
            val demandCap  = e(pkCapDemandKey).head
            (maybeCurrentBase, maybeCurrentCap) match {
              case (Some(currentBase), Some(currentCap)) =>
                val (newBase, newCap) = getNextPos(demandBase, demandCap, currentBase, currentCap)
                val newEvent = SystemEvent(cswCtx.componentInfo.prefix, encTelPosEventName)
                  .madd(
                    baseDemandKey.set(demandBase),
                    capDemandKey.set(demandCap),
                    baseCurrentKey.set(newBase),
                    capCurrentKey.set(newCap)
                  )
                publisher.publish(newEvent)
                new EventHandlerActor(ctx, cswCtx, Some(newBase), Some(newCap))
              case _ =>
                new EventHandlerActor(ctx, cswCtx, Some(demandBase), Some(demandCap))
            }
          }
          else Behaviors.same
        case x =>
          log.error(s"Expected SystemEvent but got $x")
          Behaviors.same
      }
    }

    // Simulate converging on the (base, cap) demand
    // Note from doc: Mount accepts demands at 100Hz and enclosure accepts demands at 20Hz
    private def getNextPos(targetBase: Double, targetCap: Double, currentBase: Double, currentCap: Double): (Double, Double) = {
      val speed  = 10  // deg/sec
      val rate   = 1   // hz
      val factor = 1.0 // Speedup factor for test/demo
      (
        SimulationUtil.move(speed * factor, rate, targetBase.degree, currentBase.degree).toDegree,
        SimulationUtil.move(speed * factor, rate, targetCap.degree, currentCap.degree).toDegree
      )
    }
  }

}

class EncAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {
  import EncAssemblyHandlers._
  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  log.info("Initializing ENC assembly...")
  private val subscriber        = cswCtx.eventService.defaultSubscriber
  private val eventHandler      = ctx.spawn(EventHandlerActor.make(cswCtx), "EncAssemblyEventHandler")
  private val eventSubscription = subscriber.subscribeActorRef(pkEventKeys, eventHandler)

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
