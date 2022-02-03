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
import csw.params.core.models.Coords.{AltAzCoord, EqCoord}
import csw.params.core.models.{Angle, Id}
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS
import csw.time.core.models.UTCTime
import tcs.shared.SimulationUtil

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
  private val pkAssemblyPrefix         = Prefix(TCS, "PointingKernelAssembly")
  private val pkMountDemandPosEventKey = EventKey(pkAssemblyPrefix, EventName("MountDemandPosition"))
  private val pkDemandPosKey           = KeyType.AltAzCoordKey.make("pos")
  private val pkSiderealTimeKey        = KeyType.DoubleKey.make("siderealTime")
  private val pkEventKeys              = Set(pkMountDemandPosEventKey)

  // Actor to receive Assembly events
  private object EventHandlerActor {
    private val currentPosKey: Key[AltAzCoord]   = KeyType.AltAzCoordKey.make("current")
    private val demandPosKey: Key[AltAzCoord]    = KeyType.AltAzCoordKey.make("demand")
    private val currentPosRaDecKey: Key[EqCoord] = KeyType.EqCoordKey.make("currentPos")
    private val demandPosRaDecKey: Key[EqCoord]  = KeyType.EqCoordKey.make("demandPos")
    private val mcsTelPosEventName               = EventName("MountPosition")

    def make(cswCtx: CswContext): Behavior[Event] = {
      Behaviors.setup(ctx => new EventHandlerActor(ctx, cswCtx))
    }

    /**
     *  Convert az,el to ra,dec using the given sidereal time and site latitude.
     * Algorithm based on http://www.stargazing.net/mas/al_az.htm.
     * Note: The native TPK C++ code can do this, but this assembly does not use it (The pk assembly does).
     *
     * @param st sidereal time in hours
     * @param pos the alt/az coordinates
     * @param latDeg site's latitude in deg (default: for Hawaii)
     * @return the ra.dec coords
     */
    private def altAzToRaDec(st: Double, pos: AltAzCoord, latDeg: Double = 19.82900194): EqCoord = {
      import Math._
      import Angle._
      val lat = latDeg.degree.toRadian
      val alt = pos.alt.toRadian
      val az  = pos.az.toRadian
      val dec = asin(sin(alt) * sin(lat) + cos(alt) * cos(lat) * cos(az)).radian
      val s   = acos((sin(alt) - sin(lat) * sin(dec.toRadian)) / (cos(lat) * cos(dec.toRadian))).radian
      val s2  = if (sin(az) > 0) 360 - s.toDegree else s.toDegree
      val ra1 = st - s2 / 15
      val ra  = (if (ra1 < 0) ra1 + 24 else ra1).arcHour
      EqCoord(ra, dec, tag = pos.tag)
    }
  }

  private class EventHandlerActor(ctx: ActorContext[Event], cswCtx: CswContext) extends AbstractBehavior[Event](ctx) {
    import EventHandlerActor._
    import cswCtx._
    private val log                                 = loggerFactory.getLogger
    private val publisher                           = cswCtx.eventService.defaultPublisher
    private var count                               = 0
    private var maybeCurrentPos: Option[AltAzCoord] = None

    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent if e.eventKey == pkMountDemandPosEventKey && e.paramSet.isEmpty =>
          // Check for corrupted event from C with Embedded Redis:
          log.error(s"MCS Received empty event: $e")

        case e: SystemEvent if e.eventKey == pkMountDemandPosEventKey =>
          // Note from doc: Mount accepts demands at 100Hz a nd enclosure accepts demands at 20Hz
          // Assuming we are receiving MountDemandPosition events at 100hz, we want to publish at 1hz.
          count = (count + 1) % 100
          val altAzCoordDemand = e(pkDemandPosKey).head

          maybeCurrentPos match {
            case Some(currentPos) =>
              val newAltAzPos = getNextPos(altAzCoordDemand, currentPos)
              if (count == 0) {
                // Add RA, Dec values for the demand and current positions
                val siderealTimeHours = e(pkSiderealTimeKey).head
                val currentPosRaDec   = altAzToRaDec(siderealTimeHours, newAltAzPos)
                val demandPosRaDec    = altAzToRaDec(siderealTimeHours, altAzCoordDemand)
                val newEvent = SystemEvent(cswCtx.componentInfo.prefix, mcsTelPosEventName)
                  .madd(
                    currentPosKey.set(newAltAzPos),
                    demandPosKey.set(altAzCoordDemand),
                    currentPosRaDecKey.set(currentPosRaDec),
                    demandPosRaDecKey.set(demandPosRaDec)
                  )
                publisher.publish(newEvent)
              }
              maybeCurrentPos = Some(newAltAzPos)
            case None =>
              maybeCurrentPos = Some(altAzCoordDemand)
          }
        case x =>
          log.error(s"Expected SystemEvent but got $x")
      }

      Behaviors.same
    }

    // Simulate converging on the demand target
    private def getNextPos(demandPos: AltAzCoord, currentPos: AltAzCoord): AltAzCoord = {
//      val altDiff = math.abs(demandPos.alt.uas - currentPos.alt.uas) * Angle.Uas2S
//      val azDiff  = math.abs(demandPos.az.uas - currentPos.az.uas) * Angle.Uas2S
//      log.info(s"MCS getNextPos diff alt: $altDiff arcsec, az: $azDiff arcsec")

      // The max slew for az is 2.5 deg/sec.  Max for el is 1.0 deg/sec
      val azSpeed = 2.5  // deg/sec
      val elSpeed = 1.0  // deg/sec
      val rate    = 100  // hz
      val factor  = 10.0 // Speedup factor for test/demo
      AltAzCoord(
        demandPos.tag,
        SimulationUtil.move(elSpeed * factor, rate, demandPos.alt, currentPos.alt),
        SimulationUtil.move(azSpeed * factor, rate, demandPos.az, currentPos.az)
      )
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
