addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0")

addSbtPlugin("org.scalameta"    % "sbt-scalafmt"        % "2.4.2")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"       % "1.8.2")
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"       % "0.10.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates"         % "0.5.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.5")
addSbtPlugin("org.bytedeco"     % "sbt-javacpp"         % "1.17")

classpathTypes += "maven-plugin"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  //"-Xfatal-warnings",
  "-Xlint:-unused,_",
  "-Ywarn-dead-code"
)
