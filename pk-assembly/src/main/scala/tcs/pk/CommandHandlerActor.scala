package tcs.pk

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import csw.framework.models.CswContext
import CommandHandlerActor._
import akka.actor.typed.Behavior
import csw.params.commands.ControlCommand
import csw.params.core.generics.{Key, KeyType}
import csw.params.events.{Event, EventName, SystemEvent}
import csw.time.core.models.UTCTime

import scala.concurrent.ExecutionContextExecutor

object CommandHandlerActor {
  sealed trait CommandMessage
  case class SubmitCommand(cmd: ControlCommand)        extends CommandMessage
  case object GoOnlineMessage        extends CommandMessage
  case object GoOfflineMessage        extends CommandMessage

  def make(cswCtx: CswContext): Behavior[CommandMessage] = {
    Behaviors.setup(ctx => new CommandHandlerActor(ctx, cswCtx))
  }
}

class CommandHandlerActor(ctx: ActorContext[CommandMessage], cswCtx: CswContext) extends AbstractBehavior[CommandMessage](ctx) {

  import cswCtx._
  import scala.concurrent.duration._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout             = Timeout(5.seconds)
  private val log                           = loggerFactory.getLogger

  override def onMessage(msg: CommandMessage): Behavior[CommandMessage] = {
    msg match {
      case SubmitCommand(cmd) =>
      case GoOnlineMessage     =>
      case GoOfflineMessage =>
    }
    Behaviors.same
  }
}
