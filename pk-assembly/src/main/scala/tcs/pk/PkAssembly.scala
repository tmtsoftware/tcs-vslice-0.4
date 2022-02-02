package tcs.pk

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandIssue.{
  MissingKeyIssue,
  ParameterValueOutOfRangeIssue,
  UnsupportedCommandIssue,
  WrongInternalStateIssue,
  WrongParameterTypeIssue
}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.{AltAzCoord, Coord, EqCoord}
import csw.params.core.models.{Angle, Choice, Id}
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
  private val basePosKey: Key[Coord] = KeyType.CoordKey.make("base")

  // Keys for telescope offsets in arcsec
  private val xCoordinateKey: Key[Double] = KeyType.DoubleKey.make("Xcoordinate")
  private val yCoordinateKey: Key[Double] = KeyType.DoubleKey.make("Ycoordinate")
  // TODO: Add other ref frames, update TCS API in icd database to use enum type to avoid errors
  private val refFrameKey: Key[Choice] = KeyType.ChoiceKey.make("Refframe", "ICRS", "FK5", "AzEl")

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
    if (!(setup.exists(basePosKey) && setup(basePosKey).size > 0)) {
      Invalid(runId, MissingKeyIssue(s"required SlewToTarget command key: $basePosKey is missing."))
    }
    else {
      val targetPos = setup(basePosKey).head
      targetPos match {
        case EqCoord(_, ra, dec, frame, _, _) =>
          if (ra.toDegree < 0.0 || ra.toDegree >= 360.0)
            Invalid(runId, ParameterValueOutOfRangeIssue(s"RA value out of range: ${ra.toDegree * Angle.D2H} hours"))
          else if (dec.toDegree < -90.0 || dec.toDegree > 90.0)
            Invalid(runId, ParameterValueOutOfRangeIssue(s"Dec value out of range: ${dec.toDegree} deg"))
          else
            Accepted(runId)
        case AltAzCoord(_, alt, az) =>
          if (az.toDegree < 0.0 || az.toDegree >= 360.0)
            Invalid(runId, ParameterValueOutOfRangeIssue(s"Az value out of range: ${az.toDegree} deg"))
          else if (alt.toDegree < -90.0 || alt.toDegree > 90.0)
            Invalid(runId, ParameterValueOutOfRangeIssue(s"Alt value out of range: ${alt.toDegree} deg"))
          else
            Accepted(runId)
        case x =>
          Invalid(
            runId,
            WrongParameterTypeIssue(s"Expected base position to be of type EqCoord or AltAzCoord, but got: ${x.getClass.getName}")
          )
      }
    }
  }

  private def validateOffset(runId: Id, setup: Setup): ValidateCommandResponse = {
    if (!(setup.exists(xCoordinateKey) && setup.exists(yCoordinateKey)))
      Invalid(runId, MissingKeyIssue(s"required SetOffset command keys: $xCoordinateKey or $yCoordinateKey."))
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
          val pos = setup(basePosKey).head
          slewToTarget(runId, pos)
        case "SetOffset" =>
          val x        = setup(xCoordinateKey).head
          val y        = setup(yCoordinateKey).head
          val refFrame = if (setup.exists(refFrameKey)) setup(refFrameKey).head.name else "ICRS"
          log.info(s"pk assembly: SetOffset $x, $y arcsec ($refFrame)")
          setOffset(x, y, refFrame)
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
            setOffset(0.0, 0.0, "ICRS")
            tpkc.newICRSTarget(ra.toDegree, dec.toDegree)
          case FK5 =>
            setOffset(0.0, 0.0, "FK5")
            tpkc.newFK5Target(ra.toDegree, dec.toDegree)
        }
        CommandResponse.Completed(runId)
      case AltAzCoord(_, alt, az) =>
        setOffset(0.0, 0.0, "AzEl")
        log.info(s"SlewToTarget ${Angle.deToString(alt.toRadian)}, ${Angle.raToString(az.toRadian)} (Alt/Az)")
        tpkc.newAzElTarget(az.toDegree, alt.toDegree)
        CommandResponse.Completed(runId)
      case x =>
        CommandResponse.Error(runId, s"Unsupported coordinate type: $x")
    }
  }

  // Set a telescope offset in arcsec
  // TODO: Support all ref frames listed in TCS docs
  private def setOffset(x: Double, y: Double, refFrame: String): Unit = {
    refFrame match {
      case "ICRS" => tpkc.setICRSOffset(x, y)
      case "FK5"  => tpkc.setFK5Offset(x, y)
      case "AzEl" => tpkc.setAzElOffset(x, y)
      case x      => log.error(s"Unsupported reference frame for SetOffset: $x")
    }
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
