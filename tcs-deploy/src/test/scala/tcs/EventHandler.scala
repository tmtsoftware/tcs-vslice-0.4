package tcs

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.logging.api.scaladsl.Logger
import csw.logging.client.scaladsl.LoggerFactory
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Angle
import csw.params.core.models.Coords.AltAzCoord
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
  private val mcsTelPosEventName             = EventName("MountPosition")
  private val currentPosKey: Key[AltAzCoord] = KeyType.AltAzCoordKey.make("current")
  private val demandPosKey: Key[AltAzCoord]  = KeyType.AltAzCoordKey.make("demand")
  //    private val currentPosRaDecKey: Key[EqCoord] = KeyType.EqCoordKey.make("currentPos")
  //    private val demandPosRaDecKey: Key[EqCoord]  = KeyType.EqCoordKey.make("demandPos")

  // ENC event
  private val encTelPosEventName          = EventName("CurrentPosition")
  private val baseDemandKey: Key[Double]  = KeyType.DoubleKey.make("baseDemand")
  private val capDemandKey: Key[Double]   = KeyType.DoubleKey.make("capDemand")
  private val baseCurrentKey: Key[Double] = KeyType.DoubleKey.make("baseCurrent")
  private val capCurrentKey: Key[Double]  = KeyType.DoubleKey.make("capCurrent")
}

class EventHandler(ctx: ActorContext[Event], testActor: ActorRef[EventHandler.TestActorMessages]) extends AbstractBehavior[Event](ctx) {
  import EventHandler._

  var maybeDemandPos: Option[AltAzCoord]  = None
  var maybeCurrentPos: Option[AltAzCoord] = None
  var maybeBaseDemand: Option[Double]     = None
  var maybeBaseCurrent: Option[Double]    = None
  var maybeCapDemand: Option[Double]      = None
  var maybeCapCurrent: Option[Double]     = None

  var matchCount = 0

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
      val posDiffAlt = math.abs(maybeDemandPos.get.alt.uas - maybeCurrentPos.get.alt.uas)
      val posDiffAz  = math.abs(maybeDemandPos.get.az.uas - maybeCurrentPos.get.az.uas)
      val baseDiff   = math.abs(maybeBaseDemand.get - maybeBaseCurrent.get)
      val capDiff    = math.abs(maybeCapDemand.get - maybeCapCurrent.get)

      if (posDiffAlt > closeEnoughUas)
        log.info(s"DemandPos Alt and CurrentPos Alt do not match: Diff: ${posDiffAlt * Angle.Uas2S} arcsec")
      if (posDiffAz > closeEnoughUas)
        log.info(s"DemandPos Az and CurrentPos Az do not match: Diff: ${posDiffAz * Angle.Uas2S} arcsec")
      if (baseDiff > closeEnoughUas)
        log.info(s"baseDemand and Current base do not match: Diff: $baseDiff")
      if (capDiff > closeEnoughUas)
        log.info(s"capDemand and Current cap do not match: Diff: $capDiff")

      posDiffAlt < closeEnoughUas && posDiffAz < closeEnoughUas && baseDiff < closeEnoughUas && capDiff < closeEnoughUas
    }
  }

  override def onMessage(msg: Event): Behavior[Event] = {
//    log.info(s"XXX XXX received event: $msg")
    msg match {
      case e: SystemEvent if e.eventName == mcsTelPosEventName =>
        maybeDemandPos = Some(e(demandPosKey).head)
        maybeCurrentPos = Some(e(currentPosKey).head)

      case e: SystemEvent if e.eventName == encTelPosEventName =>
        maybeBaseDemand = Some(e(baseDemandKey).head)
        maybeCapDemand = Some(e(capDemandKey).head)
        maybeBaseCurrent = Some(e(baseCurrentKey).head)
        maybeCapCurrent = Some(e(capCurrentKey).head)

      case x =>
        log.error(s"Unexpected event: $x")
    }

    if (isDemandMatched) {
      testActor ! DemandMatched
      matchCount = matchCount + 1
      log.info(s"Matched demand $matchCount")
    }
    if (matchCount >= 3)
      Behaviors.stopped
    else
      Behaviors.same
  }
}
