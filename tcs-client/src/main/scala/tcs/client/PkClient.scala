package tcs.client

import java.net.InetAddress
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import csw.command.client.CommandServiceFactory
import csw.location.api.models.ComponentId
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqCoord
import csw.params.core.models.Coords.EqFrame.FK5
import csw.params.core.models.{ObsId, ProperMotion}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.{CSW, TCS}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// A client to test locating the pk assembly and sending it a command
object PkClient extends App {
  val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "TestAssemblyClientSystem")
  import typedSystem.executionContext

  private val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("PkClient", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting PkClient")

  val locationService           = HttpLocationServiceFactory.makeLocalClient(typedSystem)
  implicit val timeout: Timeout = Timeout(10.seconds)

  private val obsId                = ObsId("2021A-001-123")
  private val posKey: Key[EqCoord] = KeyType.EqCoordKey.make("pos")

  private val prefix      = Prefix("TCS.pk-client")
  private val commandName = CommandName("SlewToTarget")

  // TODO: specify command/coords via command line args
  private def makeSetup(): Setup = {
    val pm = ProperMotion(0.5, 2.33)
    val eqCoord = EqCoord(
      ra = "12:13:14.15",
      dec = "-30:31:32.3",
      frame = FK5,
      pmx = pm.pmx,
      pmy = pm.pmy
    )
    val posParam = posKey.set(
      eqCoord
    )
    Setup(prefix, commandName, Some(obsId)).add(posParam)
  }

  val connection = AkkaConnection(ComponentId(Prefix(TCS, "PointingKernelAssembly"), Assembly))
  Await.result(locationService.resolve(connection, timeout.duration), timeout.duration) match {
    case None =>
      log.error(s"Assembly connection not found: $connection")
    case Some(loc) =>
      val assembly = CommandServiceFactory.make(loc)(typedSystem)
      assembly.submitAndWait(makeSetup()).onComplete {
        case Success(response) =>
          log.info(s"Single Submit Test Passed: Responses = $response")
          typedSystem.terminate()
        case Failure(ex) =>
          log.info(s"Single Submit Test Failed: $ex")
          typedSystem.terminate()
      }
  }
}
