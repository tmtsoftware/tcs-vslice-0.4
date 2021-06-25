package tcs.pk

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import csw.framework.models.CswContext
import csw.params.commands.CommandResponse
import csw.params.core.generics.{Key, KeyType}
import tcs.pk.wrapper.TpkWrapper
import CommandHandlerActor.SubmitCommand
import csw.params.core.models.Angle
import csw.params.core.models.Coords.EqCoord
import tcs.pk.SetTargetCmdActor.posKey

import scala.concurrent.ExecutionContextExecutor

object SetTargetCmdActor {
  def make(cswCtx: CswContext, tpkWrapper: TpkWrapper): Behavior[SubmitCommand] = {
    Behaviors.setup(ctx => new SetTargetCmdActor(ctx, cswCtx, tpkWrapper))
  }

  // Key to get the position value from a command
  val posKey: Key[EqCoord] = KeyType.EqCoordKey.make("position")

}

class SetTargetCmdActor(
    ctx: ActorContext[SubmitCommand],
    cswCtx: CswContext,
    tpkWrapper: TpkWrapper
) extends AbstractBehavior[SubmitCommand](ctx) {
  import cswCtx._
  import scala.concurrent.duration._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout             = Timeout(5.seconds)
  private val log                           = loggerFactory.getLogger

  override def onMessage(msg: SubmitCommand): Behavior[SubmitCommand] = {
    msg match {
      case cmd =>
        log.info("Inside SetTargetCmdActor: SetTargetCmd Received")
        handleSubmitCommand(cmd)
        Behaviors.same
    }
    Behaviors.same
  }

  private def handleSubmitCommand(cmd: SubmitCommand): Unit = {
    val msg = cmd.cmd
    log.info("Inside SetTargetCmdActor: handleSubmitCommand start")
    val pos = msg.paramType.get(posKey).map(_.head).get
    val posStr = s"${Angle.raToString(pos.ra.toRadian)} ${Angle.deToString(pos.dec.toRadian)}"
    log.info(s"Inside SetTargetCmdActor: handleSubmitCommand: pos is $posStr")
    tpkWrapper.newTarget(pos.ra.toDegree, pos.dec.toDegree)
    cswCtx.commandResponseManager.updateCommand(new CommandResponse.Completed(cmd.runId))
    log.info("Inside SetTargetCmdActor: command message handled")
  }

}
