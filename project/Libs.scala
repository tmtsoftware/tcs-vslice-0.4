import java.io.FileReader
import java.util.Properties

import sbt._

import scala.util.control.NonFatal

object Libs {
  val ScalaVersion = "3.6.2"

  val `jnr-ffi` = "com.github.jnr" % "jnr-ffi" % "2.2.17"

  val `pekko-actor-testkit-typed` = "org.apache.pekko" %% "pekko-actor-testkit-typed" % "1.1.2"
  val `scalatest`                = "org.scalatest"     %% "scalatest"                % "3.2.19" // Apache License 2.0
//  val `dotty-cps-async`           = "com.github.rssh"        %% "dotty-cps-async"           % "0.9.23"
}

object CSW {

  // If you want to change CSW version, then update "csw.version" property in "build.properties" file
  // Same "csw.version" property is used in "scripts/csw-services.sh" script,
  // this makes sure that CSW library dependency and CSW services version is in sync
  val Version: String = {
    var reader: FileReader = null
    try {
      val properties = new Properties()
      reader = new FileReader("project/build.properties")
      properties.load(reader)
      val version = properties.getProperty("csw.version")
      println(s"[info]] Using CSW version [$version] ***********")
      version
    }
    catch {
      case NonFatal(e) =>
        e.printStackTrace()
        throw e
    }
    finally reader.close()
  }

  val `csw-framework` = "com.github.tmtsoftware.csw" %% "csw-framework" % Version
  val `csw-testkit`   = "com.github.tmtsoftware.csw" %% "csw-testkit"   % Version
}
