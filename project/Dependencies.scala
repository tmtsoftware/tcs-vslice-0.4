import sbt._

object Dependencies {

  val shared = Seq(
    CSW.`csw-framework`,
    Libs.`scalatest` % Test
  )

  val `pk-assembly` = Seq(
    CSW.`csw-framework`,
    Libs.`jnr-ffi`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test
  )

  val `enc-assembly` = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test
  )

  val `enc-hcd` = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test
  )

  val `mcs-assembly` = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test
  )

  val `mcs-hcd` = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test
  )

  val `tcs-client` = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`scalatest` % Test
  )

  val TcsDeploy = Seq(
    CSW.`csw-framework`,
    CSW.`csw-testkit` % Test,
    Libs.`pekko-actor-testkit-typed` % Test
  )
}
