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
import csw.params.commands.CommandResponse.isNegative
import csw.params.commands.Setup
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqCoord
import csw.params.core.models.{Angle, ProperMotion}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.TCS

import scala.concurrent.Await
import scala.concurrent.duration._

//noinspection DuplicatedCode
// A client to test locating the pk assembly and sending it a command
object PkClient extends App {
  val posKey: Key[EqCoord] = KeyType.EqCoordKey.make("pos")

  // Keys for telescope offsets in arcsec
  private val xCoordinate: Key[Double] = KeyType.DoubleKey.make("Xcoordinate")
  private val yCoordinate: Key[Double] = KeyType.DoubleKey.make("Ycoordinate")

  implicit val timeout: Timeout = Timeout(10.seconds)
  val prefix                    = Prefix("TCS.pk_client")

  Options.parse(args, run)

  // Run the application
  private def run(options: Options): Unit = {

    val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "TestAssemblyClientSystem")

    val host = InetAddress.getLocalHost.getHostName
    LoggingSystemFactory.start("PkClient", "0.1", host, typedSystem)
    val log = GenericLoggerFactory.getLogger
    log.info("Starting PkClient")

    val locationService = HttpLocationServiceFactory.makeLocalClient(typedSystem)
    val obsId           = options.obsId
    val commandName     = options.command

    def makeSetup(): Option[Setup] = {
      commandName.name match {
        case "SlewToTarget" =>
          val pm = ProperMotion(options.pmx, options.pmy)
          val eqCoord = EqCoord(
            ra = options.ra,
            dec = options.dec,
            frame = options.frame,
            pmx = pm.pmx,
            pmy = pm.pmy
          )
          val posParam = posKey.set(
            eqCoord
          )
          Some(Setup(prefix, commandName, Some(obsId)).add(posParam))
        case "SetOffset" =>
          val x = xCoordinate.set(options.x)
          val y = yCoordinate.set(options.y)
          Some(Setup(prefix, commandName, Some(obsId)).add(x).add(y))

        case _ => None
      }
    }

    val connection = AkkaConnection(ComponentId(Prefix(TCS, "PointingKernelAssembly"), Assembly))
    Await.result(locationService.resolve(connection, timeout.duration), timeout.duration) match {
      case None =>
        log.error(s"Assembly connection not found: $connection")
      case Some(loc) =>
        val assembly = CommandServiceFactory.make(loc)(typedSystem)
        makeSetup().foreach { setup =>
          val result = Await.result(assembly.submitAndWait(setup), timeout.duration)
          if (isNegative(result)) log.error(s"$result")
          else log.info(s"$result")
        }
    }
    typedSystem.terminate()
  }
}
