import com.typesafe.sbt.packager.Keys.{dockerBaseImage, dockerCommands, dockerExposedPorts, maintainer}
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.{url, _}

object Common extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = JvmPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    organization := "com.github.tmtsoftware.tcs",
    organizationName := "TMT",
    scalaVersion := Libs.ScalaVersion,
    organizationHomepage := Some(url("http://www.tmt.org")),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
      "-Ywarn-dead-code"
    ),
    javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
    testOptions in Test ++= Seq(
      // show full stack traces and test case durations
      Tests.Argument("-oDF"),
      // -v Log "test run started" / "test started" / "test run finished" events on log level "info" instead of "debug".
      // -a Show stack traces and exception class name for AssertionErrors.
      Tests.Argument(TestFrameworks.JUnit, "-v", "-a")
    ),
    resolvers += "jitpack" at "https://jitpack.io",
    version := "0.0.1",
    fork := true,
    parallelExecution in Test := false,
    autoCompilerPlugins := true,
    if (formatOnCompile) scalafmtOnCompile := true else scalafmtOnCompile := false
  )

  private def formatOnCompile =
    sys.props.get("format.on.compile") match {
      case Some("false") ⇒ false
      case _             ⇒ true
    }

  // Customize the Docker install
  lazy val dockerSettings = Seq(
    maintainer := "TMT Software",
    dockerExposedPorts ++= Seq(9753),
    dockerBaseImage := "ubuntu:21.04",
    // See https://github.com/sbt/sbt-native-packager/issues/1417, replace with dockerCommandsPrepend later
    dockerCommands := dockerCommands.value.flatMap {
      case Cmd("USER", args @ _*) if args.contains("1001:0") =>
        Seq(
          Cmd("RUN", "apt update -y"),
          Cmd("RUN", "apt install -y openjdk-11-jdk libcbor-dev uuid-dev libhiredis-dev"),
          Cmd("USER", args: _*)
        )
      case cmd => Seq(cmd)
    },
    dockerCommands ++= Seq(
//      Cmd("RUN", "apt install -y libcbor-dev uuid-dev libhiredis-dev"),
    )
  )
}
