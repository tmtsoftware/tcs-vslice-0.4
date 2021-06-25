package tcs.pk

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import csw.framework.models.CswContext
import CommandHandlerActor._
import akka.actor.typed.{ActorRef, Behavior}
import csw.params.commands.ControlCommand
import csw.params.core.models.Id
import tcs.pk.EventHandlerActor.EventMessage
import tcs.pk.wrapper.TpkWrapper

import scala.concurrent.ExecutionContextExecutor

object CommandHandlerActor {
  sealed trait CommandMessage
  case class SubmitCommand(runId: Id, cmd: ControlCommand) extends CommandMessage
  case object GoOnlineMessage                              extends CommandMessage
  case object GoOfflineMessage                             extends CommandMessage

  def make(cswCtx: CswContext, online: Boolean, eventHandlerActor: ActorRef[EventMessage]): Behavior[CommandMessage] = {
    Behaviors.setup(ctx => new CommandHandlerActor(ctx, cswCtx, online, eventHandlerActor))
  }
}

class CommandHandlerActor(
    ctx: ActorContext[CommandMessage],
    cswCtx: CswContext,
    online: Boolean,
    eventHandlerActor: ActorRef[EventMessage]
) extends AbstractBehavior[CommandMessage](ctx) {

  import cswCtx._
  import scala.concurrent.duration._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout             = Timeout(5.seconds)
  private val log                           = loggerFactory.getLogger

  // XXX TODO FIXME (pass in to actor?)
  private var tpkWrapper: TpkWrapper = _

  initiateTpkEndpoint()

  override def onMessage(msg: CommandMessage): Behavior[CommandMessage] = {
    msg match {
      case SubmitCommand(runId, cmd) if cmd.commandName.name == "setTarget" =>
        log.info("Inside CommandHandlerActor: SetTargetMessage Received")
        handleSetTargetCommand(runId, cmd)
        Behaviors.same

      // XXX FIXME TODO: Maybe just make online a var!
      case GoOnlineMessage =>
        log.info("Inside CommandHandlerActor: GoOnlineMessage Received")
        // change the behavior to online
        CommandHandlerActor.make(cswCtx, online = true, eventHandlerActor)
      case GoOfflineMessage =>
        CommandHandlerActor.make(cswCtx, online = false, eventHandlerActor)
    }
  }

  private def handleSetTargetCommand(runId: Id, cmd: ControlCommand): Unit = {
    log.info(s"Inside CommandHandlerActor: handleSetTargetCommand = $cmd")
    // XXX TODO FIXME: Clean up existing actor?
    if (online) {
      val setTargetCmdActor = ctx.spawnAnonymous(SetTargetCmdActor.make(cswCtx, tpkWrapper))
      setTargetCmdActor ! SubmitCommand(runId, cmd)
    }
  }

  /**
   * This helps in initializing TPK JNI Wrapper in separate thread, so that
   * New Target and Offset requests can be passed on to it
   */
  def initiateTpkEndpoint(): Unit = {
    log.debug("Inside JPkCommandHandlerActor: initiateTpkEndpoint")
    tpkWrapper = new TpkWrapper(eventHandlerActor)
    new Thread(new Runnable() {
      override def run(): Unit = {
        tpkWrapper.initiate()
      }
    }).start()
    try Thread.sleep(100, 0)
    catch {
      case e: InterruptedException =>
        log.error("Inside TpkCommandHandler: initiateTpkEndpoint: Error is: " + e)
    }
  }

}
