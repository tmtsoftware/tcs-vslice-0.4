package tcs.pk

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import csw.framework.models.CswContext
import CommandHandlerActor._
import akka.actor.typed.{ActorRef, Behavior}
import csw.params.commands.ControlCommand
import csw.params.core.generics.KeyType
import csw.params.core.models.Id
import csw.params.events.{EventKey, EventName, SystemEvent}
import tcs.pk.EventHandlerActor.EventMessage
import tcs.pk.wrapper.TpkWrapper

import scala.concurrent.ExecutionContextExecutor

object CommandHandlerActor {
  sealed trait CommandMessage
  case class SubmitCommand(runId: Id, cmd: ControlCommand) extends CommandMessage
  case object GoOnlineMessage                              extends CommandMessage
  case object GoOfflineMessage                             extends CommandMessage

  private val demandsParamKey =     KeyType.AltAzCoordKey.make("demands")


  def make(cswCtx: CswContext, eventHandlerActor: ActorRef[EventMessage]): Behavior[CommandMessage] = {
    Behaviors.setup(ctx => new CommandHandlerActor(ctx, cswCtx, eventHandlerActor))
  }
}

class CommandHandlerActor(
    ctx: ActorContext[CommandMessage],
    cswCtx: CswContext,
    eventHandlerActor: ActorRef[EventMessage]
) extends AbstractBehavior[CommandMessage](ctx) {

  import cswCtx._
  import scala.concurrent.duration._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout             = Timeout(5.seconds)
  private val log                           = loggerFactory.getLogger
  private var online                        = true
  private val demandsEventName = EventKey(componentInfo.prefix, EventName("demands"))

  private val tpkWrapper: TpkWrapper = new TpkWrapper(eventHandlerActor)

//  startSubscribingToEvents()
  initiateTpkEndpoint()

  override def onMessage(msg: CommandMessage): Behavior[CommandMessage] = {
    msg match {
      case SubmitCommand(runId, cmd) if cmd.commandName.name == "setTarget" =>
        log.info("Inside CommandHandlerActor: SetTargetMessage Received")
        handleSetTargetCommand(runId, cmd)

      case GoOnlineMessage =>
        log.info("Inside CommandHandlerActor: GoOnlineMessage Received")
        online = true
      case GoOfflineMessage =>
        online = false
    }
    Behaviors.same
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
    new Thread(new Runnable() {
      override def run(): Unit = {
        tpkWrapper.initiate()
      }
    }).start()
  }

//  private def startSubscribingToEvents(): Unit = {
//    val subscriber = eventService.defaultSubscriber
//    val publisher = eventService.defaultPublisher
//    val eventHandlerActor =
//      ctx.spawn(eventHandler(log, publisher), "pkEventHandlerActor")
//    subscriber.subscribeActorRef(Set(hcdEventKey), eventHandlerActor)
//  }


}
