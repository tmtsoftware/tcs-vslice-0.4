package tcs.tcsdeploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object TcsContainerCmdApp extends App {
  ContainerCmd.start("tcs_deploy", Subsystem.withNameInsensitive("TCS"), args)
}
