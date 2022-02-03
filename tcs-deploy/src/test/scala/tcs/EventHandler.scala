package tcs

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.LoggerFactory
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Angle
import csw.params.core.models.Coords.EqCoord
import csw.params.events.{Event, EventName, SystemEvent}
import csw.prefix.models.Prefix

// Actor to receive Assembly events
object EventHandler {

  // error tolerance for demand/current position
  private val closeEnoughUas = 0.2 * Angle.S2Uas // 0.2 arcsecs converted to microarcsecs

  sealed trait TestActorMessages
  // Message sent to wait for current positions to match demand
  case class MatchDemand(replyTo: ActorRef[Boolean]) extends TestActorMessages
  // Message sent when demand has been matched
  case object DemandMatched extends TestActorMessages
  case object StopTest      extends TestActorMessages

  object TestActor {
    def make(): Behavior[TestActorMessages] = {
      Behaviors.setup(ctx => new TestActor(ctx))
    }
  }
  class TestActor(ctx: ActorContext[TestActorMessages]) extends AbstractBehavior[TestActorMessages](ctx) {
    var maybeReplyTo: Option[ActorRef[Boolean]] = None

    override def onMessage(msg: TestActorMessages): Behavior[TestActorMessages] = {
      msg match {
        case MatchDemand(replyTo) =>
          maybeReplyTo = Some(replyTo)
          Behaviors.same
        case DemandMatched =>
          maybeReplyTo.foreach(_ ! true)
          maybeReplyTo = None
          Behaviors.same
        case StopTest =>
          Behaviors.stopped
      }
    }
  }

  def make(testActor: ActorRef[TestActorMessages]): Behavior[Event] = {
    Behaviors.setup(ctx => new EventHandler(ctx, testActor))
  }

  // MCS event
  private val mcsTelPosEventName          = EventName("MountPosition")
  private val currentPosKey: Key[EqCoord] = KeyType.EqCoordKey.make("currentPos")
  private val demandPosKey: Key[EqCoord]  = KeyType.EqCoordKey.make("demandPos")

  // ENC event
  private val encTelPosEventName          = EventName("CurrentPosition")
  private val baseDemandKey: Key[Double]  = KeyType.DoubleKey.make("baseDemand")
  private val capDemandKey: Key[Double]   = KeyType.DoubleKey.make("capDemand")
  private val baseCurrentKey: Key[Double] = KeyType.DoubleKey.make("baseCurrent")
  private val capCurrentKey: Key[Double]  = KeyType.DoubleKey.make("capCurrent")
}

class EventHandler(ctx: ActorContext[Event], testActor: ActorRef[EventHandler.TestActorMessages]) extends AbstractBehavior[Event](ctx) {
  import EventHandler._

  var maybeDemandPos: Option[EqCoord]  = None
  var maybeCurrentPos: Option[EqCoord] = None
  var maybeBaseDemand: Option[Double]  = None
  var maybeBaseCurrent: Option[Double] = None
  var maybeCapDemand: Option[Double]   = None
  var maybeCapCurrent: Option[Double]  = None

  private def isEmpty =
    maybeDemandPos.isEmpty ||
      maybeCurrentPos.isEmpty ||
      maybeBaseDemand.isEmpty ||
      maybeBaseCurrent.isEmpty ||
      maybeCapDemand.isEmpty ||
      maybeCapCurrent.isEmpty

  private val prefix            = Prefix("TCS.testEventHandler")
  implicit lazy val log: Logger = new LoggerFactory(prefix).getLogger

  private def isDemandMatched: Boolean = {
    if (isEmpty) {
      log.info("Missing events")
      false
    }
    else {
      val posDiffRa  = math.abs(maybeDemandPos.get.ra.uas - maybeCurrentPos.get.ra.uas)
      val posDiffDec = math.abs(maybeDemandPos.get.dec.uas - maybeCurrentPos.get.dec.uas)
      val baseDiff   = math.abs(maybeBaseDemand.get - maybeBaseCurrent.get)
      val capDiff    = math.abs(maybeCapDemand.get - maybeCapCurrent.get)

//      if (posDiffRa > closeEnoughUas)
//        log.info(s"XXX DemandPos RA and CurrentPos RA do not match: Diff: ${posDiffRa * Angle.Uas2S} arcsec ( ${posDiffRa * Angle.Uas2D} deg)")
//      if (posDiffDec > closeEnoughUas)
//        log.info(
//          s"XXX DemandPos Dec and CurrentPos Dec do not match: Diff: ${posDiffDec * Angle.Uas2S} arcsec (${posDiffDec * Angle.Uas2D} deg)"
//        )
//      if (baseDiff > closeEnoughUas)
//        log.info(s"XXX baseDemand and Current base do not match: Diff: $baseDiff")
//      if (capDiff > closeEnoughUas)
//        log.info(s"XXX capDemand and Current cap do not match: Diff: $capDiff")

      posDiffRa < closeEnoughUas && posDiffDec < closeEnoughUas && baseDiff < closeEnoughUas && capDiff < closeEnoughUas
    }
  }

  override def onMessage(msg: Event): Behavior[Event] = {
//    log.info(s"XXX XXX Test EventHandler: received event: $msg")
    msg match {
      case e: SystemEvent if e.eventName == mcsTelPosEventName && e.paramSet.nonEmpty =>
        maybeDemandPos = Some(e(demandPosKey).head)
        maybeCurrentPos = Some(e(currentPosKey).head)

      case e: SystemEvent if e.eventName == encTelPosEventName && e.paramSet.nonEmpty =>
        maybeBaseDemand = Some(e(baseDemandKey).head)
        maybeCapDemand = Some(e(capDemandKey).head)
        maybeBaseCurrent = Some(e(baseCurrentKey).head)
        maybeCapCurrent = Some(e(capCurrentKey).head)

      case x =>
        if (!x.isInvalid)
          log.error(s"Unexpected event: $x")
    }

    if (isDemandMatched) {
      testActor ! DemandMatched
    }
    Behaviors.same
  }
}
