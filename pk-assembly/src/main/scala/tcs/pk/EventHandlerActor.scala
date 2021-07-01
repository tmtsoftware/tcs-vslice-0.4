//package tcs.pk
//
//import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
//import akka.util.Timeout
//import csw.framework.models.CswContext
//import EventHandlerActor._
//import akka.actor.typed.Behavior
//import csw.params.core.generics.{Key, KeyType}
//import csw.params.events.{Event, EventName, SystemEvent}
//import csw.time.core.models.UTCTime
//
//import scala.concurrent.ExecutionContextExecutor
//
//object EventHandlerActor {
//  sealed trait EventMessage
//  case class McsDemand(az: Double, el: Double)        extends EventMessage
//  // XXX The ICD shows these published by other assemblies!
//  case class EncDemand(base: Double, cap: Double)     extends EventMessage
//  case class M3Demand(rotation: Double, tilt: Double) extends EventMessage
//
//  def make(cswCtx: CswContext): Behavior[EventMessage] = {
//    Behaviors.setup(ctx => new EventHandlerActor(ctx, cswCtx))
//  }
//
//  private val azDoubleKey    = KeyType.DoubleKey.make("az")
//  private val elDoubleKey    = KeyType.DoubleKey.make("el")
//  private val publishTimeKey = KeyType.UTCTimeKey.make("timestamp")
//}
//
//class EventHandlerActor(ctx: ActorContext[EventMessage], cswCtx: CswContext) extends AbstractBehavior[EventMessage](ctx) {
//
//  import cswCtx._
//  import scala.concurrent.duration._
//
//  implicit val ec: ExecutionContextExecutor = ctx.executionContext
//  implicit val timeout: Timeout             = Timeout(5.seconds)
//  private val log                           = loggerFactory.getLogger
//
//  private var counterEnc = 0
//  private var counterMcs = 0
//  private var counterM3  = 0
//
//  private val LIMIT = 100000
//
//  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
//    msg match {
//      case McsDemand(az, el) =>
//        if (counterMcs < LIMIT) {
//          log.info("pk EventHandlerActor: McsDemand message received")
//          publishMcsDemand(az, el)
//          this.counterMcs += 1
//        }
//      case EncDemand(base, cap)     =>
//      // XXX TODO: see icd
//      case M3Demand(rotation, tilt) =>
//      // XXX TODO: see icd
//    }
//    Behaviors.same
//  }
//
//  private def publishMcsDemand(az: Double, el: Double): Unit = {
//    log.info("pk EventHandlerActor: Publishing Mcs Demand ")
//    val event = SystemEvent(componentInfo.prefix, EventName("currentPosition"))
//      .add(azDoubleKey.set(az))
//      .add(elDoubleKey.set(el))
//      .add(publishTimeKey.set(UTCTime.now))
//    eventService.defaultPublisher.publish(event)
//  }
//
//}
