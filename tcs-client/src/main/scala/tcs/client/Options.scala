package tcs.client

import csw.params.commands.CommandName
import csw.params.core.models.Coords.EqFrame
import csw.params.core.models.Coords.EqFrame.FK5
import csw.params.core.models.ObsId
import csw.services.BuildInfo

case class Options(
    command: CommandName = CommandName("SlewToTarget"),
    ra: String = "12:13:14.15",
    dec: String = "-30:31:32.3",
    frame: EqFrame = FK5,
    pmx: Double = 0.0,
    pmy: Double = 0.0,
    x: Double = 0.0,
    y: Double = 0.0,
    obsId: ObsId = ObsId("2021A-001-123")
)

object Options {
  private val defaults = Options()

  // Parser for the command line options
  private val parser = new scopt.OptionParser[Options]("pk-event-client") {
    head("csw-event-generator", BuildInfo.version)

    opt[String]('c', "command") valueName "<command>" action { (x, c) =>
      c.copy(command = CommandName(x))
    } text s"The command to send to the pk assembly (One of: 'SlewToTarget', SetOffset, ...): ${defaults.command})"

    opt[String]('r', "ra") valueName "<RA>" action { (x, c) =>
      c.copy(ra = x)
    } text s"The RA coordinate for the command: ${defaults.ra})"

    opt[String]('d', "dec") valueName "<Dec>" action { (x, c) =>
      c.copy(dec = x)
    } text s"The Dec coordinate for the command: ${defaults.dec})"

    opt[String]('f', "frame") valueName "<frame>" action { (x, c) =>
      c.copy(frame = EqFrame.withName(x))
    } text s"The frame of refererence for RA, Dec: ${defaults.frame})"

    opt[Double]("pmx") valueName "<pmx>" action { (x, c) =>
      c.copy(pmx = x)
    } text s"The primary motion x value: ${defaults.pmx})"

    opt[Double]("pmy") valueName "<pmy>" action { (x, c) =>
      c.copy(pmy = x)
    } text s"The primary motion y value: ${defaults.pmy})"

    opt[Double]('x', "x") valueName "<x>" action { (x, c) =>
      c.copy(x = x)
    } text s"The x offset in arcsec: ${defaults.x})"

    opt[Double]('y', "y") valueName "<y>" action { (x, c) =>
      c.copy(y = x)
    } text s"The y offset in arcsec: ${defaults.y})"

    opt[String]('o', "obsId") valueName "<id>" action { (x, c) =>
      c.copy(obsId = ObsId(x))
    } text s"The observation id: ${defaults.obsId})"

    help("help")
    version("version")
  }

  private def supportedCommands = Set("SlewToTarget", "SetOffset")

  //noinspection SameParameterValue
  private def error(msg: String): Unit = {
    println(msg)
    System.exit(1)
  }

  private def checkOptions(options: Options): Unit = {
    if (!supportedCommands.contains(options.command.name))
      error(s"Unsupported pk assembly command ${options.command}")
  }

  def parse(args: Array[String], run: Options => Unit): Unit = {
    parser.parse(args, Options()) match {
      case Some(options) =>
        try {
          checkOptions(options)
          run(options)
        }
        catch {
          case e: Throwable =>
            e.printStackTrace()
            System.exit(1)
        }
      case None => System.exit(1)
    }
  }

}
