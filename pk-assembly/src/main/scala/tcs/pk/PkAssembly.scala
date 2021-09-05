package tcs.pk

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandIssue.{MissingKeyIssue, UnsupportedCommandIssue, WrongInternalStateIssue}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.{AltAzCoord, Coord, EqCoord}
import csw.params.core.models.{Angle, Id}
import csw.time.core.models.UTCTime

import scala.concurrent.ExecutionContextExecutor
import csw.params.commands.CommandResponse.{Accepted, Invalid, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandResponse, ControlCommand, Observe, Setup}
import csw.params.core.models.Coords.EqFrame.{FK5, ICRS}
import tcs.pk.wrapper.TpkC

// --- Demo implementation of parts of the TCS pk assembly ---

class PkAssemblyBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext): ComponentHandlers =
    new PkAssemblyHandlers(ctx, cswCtx)
}

class PkAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val tpkc: TpkC                    = TpkC.getInstance()

  // Key to get the position value from a command
  // (Note: Using CoordKey here instead of the individual RA,Dec params defined in the icd database for TCS)
  private val posKey: Key[Coord] = KeyType.CoordKey.make("pos")

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
        case "SlewToTarget" =>
          val pos = setup(posKey).head
          setOffset(0.0, 0.0)
          slewToTarget(runId, pos)
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

  // Set the target position
  private def slewToTarget(runId: Id, targetPos: Coord): SubmitResponse = {
    targetPos match {
      case EqCoord(_, ra, dec, frame, _, _) =>
        log.info(s"SlewToTarget ${Angle.raToString(ra.toRadian)}, ${Angle.deToString(dec.toRadian)} ($frame)")
        frame match {
          case ICRS =>
            tpkc.newICRSTarget(ra.toDegree, dec.toDegree)
          case FK5 =>
            tpkc.newFK5Target(ra.toDegree, dec.toDegree)
        }
        CommandResponse.Completed(runId)
      case AltAzCoord(_, alt, az) =>
        log.info(s"SlewToTarget ${Angle.deToString(alt.toRadian)}, ${Angle.raToString(az.toRadian)} (Alt/Az)")
        tpkc.newAzElTarget(az.toDegree, alt.toDegree)
        CommandResponse.Completed(runId)
      case x =>
        CommandResponse.Error(runId, s"Unsupported coordinate type: $x")
    }
  }

  // Set a telescope offset in arcsec
  private def setOffset(x: Double, y: Double): Unit = {
    tpkc.setOffset(x, y)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
