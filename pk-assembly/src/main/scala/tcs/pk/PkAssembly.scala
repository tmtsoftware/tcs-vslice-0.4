package tcs.pk

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandIssue.{MissingKeyIssue, UnsupportedCommandIssue, WrongInternalStateIssue}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqCoord
import csw.params.core.models.{Angle, Id}
import csw.time.core.models.UTCTime

import scala.concurrent.ExecutionContextExecutor
import csw.params.commands.CommandResponse.{Accepted, Invalid, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandResponse, ControlCommand, Observe, Setup}
import csw.time.scheduler.api.Cancellable
import tcs.pk.wrapper.TpkC

// --- Demo implementation of parts of the TCS pk assembly ---

class PkAssemblyBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext): ComponentHandlers =
    new PkAssemblyHandlers(ctx, cswCtx)
}

class PkAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor   = ctx.executionContext
  private val log                             = loggerFactory.getLogger
  private val tpkc: TpkC                      = TpkC.getInstance()
  private var maybeTimer: Option[Cancellable] = None

  // Key to get the position value from a command
  // (Note: Using EqCoordKey here instead of the individual RA,Dec params defined in the icd database for TCS)
  private val posKey: Key[EqCoord] = KeyType.EqCoordKey.make("pos")

  // Keys for telescope offsets in arcsec
  private val xCoordinate: Key[Double] = KeyType.DoubleKey.make("Xcoordinate")
  private val yCoordinate: Key[Double] = KeyType.DoubleKey.make("Ycoordinate")

  /**
   * This helps in initializing TPK JNI Wrapper in separate thread, so that
   * New Target and Offset requests can be passed on to it
   */
  private def initiateTpkEndpoint(): Unit = {
    new Thread(new Runnable() {
      override def run(): Unit = {
        tpkc.init()
      }
    }).start()
  }

  override def initialize(): Unit = {
    log.info("Initializing pk assembly...")
    try {
      initiateTpkEndpoint()
    }
    catch {
      case ex: Exception => log.error("Failed to initialize native code", ex = ex)
    }
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, command: ControlCommand): ValidateCommandResponse = {
    if (!isOnline)
      Invalid(runId, WrongInternalStateIssue("Can't slew to target: pk assembly is offline"))
    else
      command match {
        case setup: Setup =>
          setup.commandName.name match {
            case "SlewToTarget" =>
              validateSlewToTarget(runId, setup)
            case "SetOffset" =>
              validateOffset(runId, setup)
            case x =>
              Invalid(runId, UnsupportedCommandIssue(s"Command: $x is not supported for TCS pk Assembly."))
          }
        case _ =>
          Invalid(runId, UnsupportedCommandIssue(s"Command: ${command.commandName.name} is not supported for TCS pk Assembly."))
      }
  }

  private def validateSlewToTarget(runId: Id, setup: Setup): ValidateCommandResponse = {
    if (!(setup.exists(posKey) && setup(posKey).size > 0))
      Invalid(runId, MissingKeyIssue(s"required SlewToTarget command key: $posKey is missing."))
    else
      Accepted(runId)
  }

  private def validateOffset(runId: Id, setup: Setup): ValidateCommandResponse = {
    if (!(setup.exists(xCoordinate) && setup.exists(yCoordinate)))
      Invalid(runId, MissingKeyIssue(s"required SetOffset command keys: $xCoordinate or $yCoordinate."))
    else
      Accepted(runId)
  }

  override def onSubmit(runId: Id, command: ControlCommand): SubmitResponse = {
    log.debug(s"PkAssemblyHandlers: onSubmit($runId, $command)")
    command match {
      case s: Setup => onSetup(runId, s)
      case _: Observe =>
        Invalid(runId, UnsupportedCommandIssue("Observe commands not supported"))
    }
  }

  private def onSetup(runId: Id, setup: Setup): SubmitResponse = {
    if (!isOnline)
      CommandResponse.Error(runId, "pk assembly is offline")
    else
      setup.commandName.name match {
        // XXX TODO FIXME: Add other fields from TCS ICD: Refframe, RadialVelocity (use Refframe for target)
        case "SlewToTarget" =>
          val pos    = setup(posKey).head
          val posStr = s"${Angle.raToString(pos.ra.toRadian)} ${Angle.deToString(pos.dec.toRadian)}"
          log.info(s"pk assembly: SlewToTarget $posStr")
          setOffset(0.0, 0.0)
          slewToTarget(pos)
          CommandResponse.Completed(runId)
        case "SetOffset" =>
          val x = setup(xCoordinate).head
          val y = setup(yCoordinate).head
          log.info(s"pk assembly: SetOffset $x, $y arcsec")
          setOffset(x, y)
          CommandResponse.Completed(runId)
        case _ =>
          CommandResponse.Error(runId, s"Unsupported pk assembly command: ${setup.commandName}")
      }
  }

  // Simulate converging slowly on target
  // XXX TODO FIXME temp
  private def slewToTarget(targetPos: EqCoord): Unit = {
//    val targetRa  = targetPos.ra.toDegree
//    val targetDec = targetPos.dec.toDegree
//    val curRa     = tpkc.current_position_ra()
//    val curDec    = tpkc.current_position_dec()
//    val percent   = 0.05
//    val errMargin = 0.0001
//    val newRa     = curRa + (targetRa - curRa) * percent
//    val newDec    = curDec + (targetDec - curDec) * percent
//    val newRa  = targetRa
//    val newDec = targetDec
    tpkc.newTarget(targetPos.ra.toDegree, targetPos.dec.toDegree)

    //    if (Math.abs(newRa - targetRa) > errMargin || Math.abs(newDec - targetDec) > errMargin) {
//      log.info(s"XXX Slewing to target: $targetRa, $targetDec -> Now at: $newRa, $newDec")
//      val t = timeServiceScheduler.scheduleOnce(UTCTime(UTCTime.now().value.plusMillis(200))) {
//        slewToTarget(targetPos)
//      }
//      maybeTimer = Some(t)
//    }
//    else {
//      log.info(s"XXX converged on target: $targetRa, $targetDec")
//      // done
//      tpkc.newTarget(targetRa, targetDec)
//    }
  }

//  // Simulate converging slowly on target
//  private def slewToTarget(targetPos: EqCoord): Unit = {
//    val targetRa  = targetPos.ra.toDegree
//    val targetDec = targetPos.dec.toDegree
//    val curRa     = tpkc.current_position_ra()
//    val curDec    = tpkc.current_position_dec()
//    val percent   = 0.05
//    val errMargin = 0.0001
//    val newRa     = curRa + (targetRa - curRa) * percent
//    val newDec    = curDec + (targetDec - curDec) * percent
//    maybeTimer.foreach(_.cancel())
//    tpkc.newTarget(newRa, newDec)
//    if (Math.abs(newRa - targetRa) > errMargin || Math.abs(newDec - targetDec) > errMargin) {
//      log.info(s"XXX Slewing to target: $targetRa, $targetDec -> Now at: $newRa, $newDec")
//      val t = timeServiceScheduler.scheduleOnce(UTCTime(UTCTime.now().value.plusMillis(200))) {
//        slewToTarget(targetPos)
//      }
//      maybeTimer = Some(t)
//    }
//    else {
//      log.info(s"XXX converged on target: $targetRa, $targetDec")
//      // done
//      tpkc.newTarget(targetRa, targetDec)
//    }
//  }

  // Set a telescope offset in arcsec
  private def setOffset(x: Double, y: Double): Unit = {
    tpkc.offset(x, y)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
