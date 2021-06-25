
lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `pk-assembly`,
  `enc-assembly`,
  `enc-hcd`,
  `mcs-assembly`,
  `mcs-hcd`,
  `tpk-jni`,
  `tcs-tcsdeploy`
)

lazy val `tcs-vslice-04` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

// pk assembly module
lazy val `pk-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.`pk-assembly`
  )

// ENC assembly module
lazy val `enc-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.`enc-assembly`
  )

// ENC HCD module
lazy val `enc-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.`enc-hcd`
  )

// MCS assembly module
lazy val `mcs-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.`mcs-assembly`
  )

// MCS HCD module
lazy val `mcs-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.`mcs-hcd`
  )

// Java wrapper for native TPK C lib, using cmake and swig
// XXX TODO: Call make from sbt
lazy val `tpk-jni` = project
  .settings(
  )

// deploy module
lazy val `tcs-tcsdeploy` = project
  .dependsOn(
    `pk-assembly`,
    `enc-assembly`,
    `enc-hcd`,
    `mcs-assembly`,
    `mcs-hcd`,
  )
  .enablePlugins(CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.TcsDeploy
  )
