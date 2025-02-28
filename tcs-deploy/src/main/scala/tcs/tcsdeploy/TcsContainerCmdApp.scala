package tcs.tcsdeploy

import csw.framework.deploy.containercmd.ContainerCmd
import csw.prefix.models.Subsystem

object TcsContainerCmdApp {
  def main(args: Array[String]): Unit = {
    ContainerCmd.start("tcs_deploy", Subsystem.withNameInsensitive("TCS"), args)
  }
}
