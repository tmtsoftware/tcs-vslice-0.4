//addSbtPlugin("org.scalastyle"                   %% "scalastyle-sbt-plugin"     % "1.0.0") // not scala 3 ready

addSbtPlugin("org.scalameta"    % "sbt-scalafmt"        % "2.5.4")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"       % "2.3.1")
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"       % "0.13.1")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"         % "0.6.4")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.1")

classpathTypes += "maven-plugin"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation"
)
