package tcs.tcsdeploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object TcsContainerCmdApp extends App {

  ContainerCmd.start("tcs_container_cmd_app", Subsystem.withNameInsensitive("TCS"), args)

}
