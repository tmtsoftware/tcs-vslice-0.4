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
import csw.params.commands.{CommandName, CommandResponse, ControlCommand, Observe, Setup}
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
  // (Note: Using EqCoordKey here instead of the individual RA,Dec params defined in the icd database for TCS)
  private val posKey: Key[EqCoord] = KeyType.EqCoordKey.make("pos")

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
    command match {
      case setup: Setup =>
        setup.commandName match {
          case CommandName("SlewToTarget") =>
            validateSlewToTarget(runId, setup)
          case x =>
            Invalid(runId, UnsupportedCommandIssue(s"Command: $x is not supported for TCS pk Assembly."))
        }
      case _ =>
        Invalid(runId, UnsupportedCommandIssue(s"Command: ${command.commandName.name} is not supported for TCS pk Assembly."))
    }
  }

  private def validateSlewToTarget(runId: Id, setup: Setup): ValidateCommandResponse = {
    if (!isOnline) {
      Invalid(runId, WrongInternalStateIssue("Can't slew to target: pk assembly is offline"))
    }
    else if (!setup.exists(posKey) && setup(posKey).size > 0) {
      Invalid(runId, MissingKeyIssue(s"required SlewToTarget command key: $posKey is missing."))
    }
    else {
      Accepted(runId)
    }
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
    setup.commandName match {
      // XXX TODO FIXME: tcs-vslice-0.3 had "setTarget" here: The ICD database has other commands...
      case CommandName("SlewToTarget") =>
        if (isOnline) {
          val pos    = setup(posKey).head
          val posStr = s"${Angle.raToString(pos.ra.toRadian)} ${Angle.deToString(pos.dec.toRadian)}"
          log.info(s"pk assembly: SlewToTarget $posStr")
          tpkc.newTarget(pos.ra.toDegree, pos.dec.toDegree)
          log.info(s"XXX pk assembly: after newTarget call")
          CommandResponse.Completed(runId)
        }
        else {
          CommandResponse.Error(runId, "Can't slew to target: pk assembly is offline")
        }
      case _ =>
        CommandResponse.Error(runId, s"Unsupported pk assembly command: ${setup.commandName}")
    }
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
