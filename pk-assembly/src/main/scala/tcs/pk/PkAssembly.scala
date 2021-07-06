package tcs.pk

import akka.actor.typed.scaladsl.ActorContext
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.TrackingEvent
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqCoord
import csw.params.core.models.{Angle, Id}
import csw.time.core.models.UTCTime
import tcs.pk.wrapper.TpkWrapper

import scala.concurrent.ExecutionContextExecutor
import csw.params.commands.CommandResponse.{Accepted, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandName, CommandResponse, ControlCommand}

// --- Demo implementation of parts of the TCS pk assembly ---

class PkAssemblyBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext): ComponentHandlers =
    new PkAssemblyHandlers(ctx, cswCtx)
}

//object PkAssemblyHandlers {
//  // Key to get the position value from a command
//  // (Note: Using EqCoordKey here instead of the individual RA,Dec params defined in the icd database for TCS)
//  private val posKey: Key[EqCoord] = KeyType.EqCoordKey.make("pos")
//}

class PkAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private val tpkWrapper: TpkWrapper = new TpkWrapper()

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
            tpkWrapper.initiate()
          }
        }).start()
  }

  override def initialize(): Unit = {
    log.info("Initializing pk assembly...")
    try {
      initiateTpkEndpoint()
    } catch {
      case ex: Exception => log.error("Failed to initialize native code", ex = ex)
    }
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {}

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = Accepted(runId)

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.debug(s"PkAssemblyHandlers: onSubmit($runId, $controlCommand)")

    controlCommand.commandName match {
      case CommandName("SlewToTarget") =>
        if (isOnline) {
          val pos    = controlCommand.paramType.get(posKey).map(_.head).get
          val posStr = s"${Angle.raToString(pos.ra.toRadian)} ${Angle.deToString(pos.dec.toRadian)}"
          log.info(s"pk assembly: SlewToTarget $posStr")
          tpkWrapper.newTarget(pos)
          log.info(s"XXX pk assembly: after newTarget call")
          CommandResponse.Completed(runId)
        }
        else {
          CommandResponse.Error(runId, s"Can't slew to target: pk assembly is offline")
        }
      case _ =>
        CommandResponse.Error(runId, s"Unsupported pk assembly command: ${controlCommand.commandName}")
    }
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {}

  override def onShutdown(): Unit = {}

  override def onGoOffline(): Unit = {}

  override def onGoOnline(): Unit = {}

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}

}
