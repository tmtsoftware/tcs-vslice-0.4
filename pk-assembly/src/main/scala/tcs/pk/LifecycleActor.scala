//package tcs.pk
//
//import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
//import akka.util.Timeout
//import csw.framework.models.CswContext
//import LifecycleActor._
//import akka.actor.typed.Behavior
//
//import scala.concurrent.ExecutionContextExecutor
//
//object LifecycleActor {
//  sealed trait LifecycleMessage
//  object Initialize extends LifecycleMessage
//  object Shutdown extends LifecycleMessage
//
//  def make(cswCtx: CswContext): Behavior[LifecycleMessage] = {
//    Behaviors.setup(ctx => new LifecycleActor(ctx, cswCtx))
//  }
//
//}
//
//class LifecycleActor(ctx: ActorContext[LifecycleMessage], cswCtx: CswContext)
//  extends AbstractBehavior[LifecycleMessage](ctx) {
//
//  import cswCtx._
//  import scala.concurrent.duration._
//
//  implicit val ec: ExecutionContextExecutor = ctx.executionContext
//  implicit val timeout: Timeout = Timeout(5.seconds)
//  private val log = loggerFactory.getLogger
//
//  override def onMessage(msg: LifecycleMessage): Behavior[LifecycleMessage] = {
//    msg match {
//      case Initialize =>
//        log.info("LifecycleActor: Initialize message received")
//        Behaviors.same
//      case Shutdown =>
//        log.info("LifecycleActor: Shutdown message received")
//        Behaviors.same
//    }
//  }
//}
