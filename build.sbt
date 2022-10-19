
lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  shared,
  `pk-assembly`,
  `tcs-client`,
  `tcs-deploy`,
  `enc-assembly`,
//  `enc-hcd`,
  `mcs-assembly`,
//  `mcs-hcd`,
)

lazy val `tcs-vslice-04` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

lazy val shared = project
  .settings(
    libraryDependencies ++= Dependencies.shared
  )

// pk assembly module
lazy val `pk-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.`pk-assembly`
  )

// ENC assembly module
lazy val `enc-assembly` = project
  .dependsOn(shared)
  .settings(
    libraryDependencies ++= Dependencies.`enc-assembly`
  )

//// ENC HCD module
//lazy val `enc-hcd` = project
//  .settings(
//    libraryDependencies ++= Dependencies.`enc-hcd`
//  )

// MCS assembly module
lazy val `mcs-assembly` = project
  .dependsOn(shared)
  .settings(
    libraryDependencies ++= Dependencies.`mcs-assembly`
  )

//// MCS HCD module
//lazy val `mcs-hcd` = project
//  .settings(
//    libraryDependencies ++= Dependencies.`mcs-hcd`
//  )

// Test client
lazy val `tcs-client` = project
  .enablePlugins(CswBuildInfo, DeployApp)
  .settings(
    libraryDependencies ++= Dependencies.`tcs-client`
  )

// deploy module
lazy val `tcs-deploy` = project
  .dependsOn(
    `pk-assembly`,
    `enc-assembly`,
//    `enc-hcd`,
    `mcs-assembly`,
//    `mcs-hcd`,
  )
  .enablePlugins(CswBuildInfo, DeployApp)
  .settings(
    libraryDependencies ++= Dependencies.TcsDeploy,
    NativePackagerKeys.bashScriptExtraDefines += "export LD_LIBRARY_PATH=$lib_dir/`uname`:/usr/local/lib:/usr/local/lib64; export DYLD_FALLBACK_LIBRARY_PATH=$LD_LIBRARY_PATH"
  )
