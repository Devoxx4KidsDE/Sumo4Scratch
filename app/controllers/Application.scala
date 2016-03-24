package controllers

import de.bischinger.parrot.commands.movement.Pcmd
import de.bischinger.parrot.controller.DroneController
import de.bischinger.parrot.network._
import play.api._
import play.api.libs.ws.WS
import play.api.mvc._

/**
 * This controller serves as the main "proxy" to receive Scratch's calls and routes them
 * to the respective methods of the drone controller.
 *
 * It also takes care that if a command is started there is a reporting back to Scratch as long as
 * the command is still running. Note that for simplicity currently only one command at a time is
 * allowed to be executed however this could be easily extend by not using movementId but a set of ids
 *
 * Work in Progress
 * - Reconnecting or Closing the connection is not yet as stable as I would wish. There needs to be some
 *   improvement which might be due to incorrect use of the drone library --> Tobias?
 * - All commands currently expect that sumoConnect has been called before and tragically fail if not, which of
 *   course should be improved by first asking if dronecontroller is already initialized.
 */

object Application extends Controller {

  def ip = "192.168.2.1"
  def port = 44444
  def wlan = "my wireless lan" // not sure yet what it is for. Tobias?

  var movementId: Int = 0

  var droneConnection: DroneConnection = null
  var droneController: DroneController = null

  def index = Action {
    Ok(views.html.index(""))
  }

  /**
   * This needs to report back some special crossdomain policy information allowance information that Flash
   * which Scratch is based on needs to work. This basically forwards to the crossdomain view which contains
   * that static information.
   *
   * @return
   */
  def crossdomain = Action {
    Ok(views.html.crossdomain())
  }

  def sumoConnect (ip: String, port: Int) = Action {
    Logger.info(s"Connecting: $ip : $port at $wlan")

    if (droneController!= null)
      droneController.close()
    try {
      // withoutQueue, so commands become synchronous
      droneConnection = new WirelessLanDroneConnection(ip, port, wlan)
      droneController = new DroneController(droneConnection)
      Ok
    } catch {
      case e: Exception => ServiceUnavailable
    }
    Ok
  }

  def sumoClose = Action {
    Logger.info(s"Closing connection: $ip : $port at $wlan")
    if (droneConnection!=null && droneConnection != null) {
      droneController.close()
    }
    Ok
  }

  def forward (id: Int) = Action {
    Logger.info(s"vorwärts ($id)")
    runAndMonitorCommand (id) {
      droneController.forward()
    }
    Ok

  }

  def forward2 (id: Int, speed: Int, timeMs: Int) = Action {
    Logger.info(s"vorwärts ($id) mit der Geschwindigkeit $speed für $timeMs Millisekunden")
    runAndMonitorCommand (id) {
        droneController.send(Pcmd.pcmd(speed, 0, timeMs))
    }
    Ok
  }

  def backward (id: Int) = Action {
    Logger.info("rückwärts")
    runAndMonitorCommand (id) {
      droneController.backward()
    }
    Ok
  }

  def left (id: Int) = Action {
    Logger.info("links")
    runAndMonitorCommand (id) {
        droneController.left()
    }
    Ok

  }

  def left2 (id: Int, degrees: Int) = Action {
    Logger.info("links $degrees")
    runAndMonitorCommand (id) {
        droneController.left(degrees)
    }
    Ok
  }

  def right (id: Int) = Action {
    Logger.info("rechts")
    runAndMonitorCommand (id) {
      droneController.right()
    }
    Ok
  }

  def right2 (id: Int, degrees: Int) = Action {
    Logger.info("rechts $degrees")
    runAndMonitorCommand (id) {
      droneController.right(degrees)
    }
    Ok
  }

  def jump (id: Int, jumpType:String) = Action {
    Logger.info(s"springe $jumpType")

    runAndMonitorCommand (id) {
      jumpType match {
        case "hoch" => droneController.jumpHigh()
        case "weit" => droneController.jumpLong()
      }
    }
    Ok
  }

  def trick (id: Int, trick:String) = Action {
    Logger.info(s"springe $trick")

    runAndMonitorCommand (id) {
      trick match {
        case "Drehung" => droneController.spin()
        case "Drehsprung" => droneController.spinJump()
        case "Tippen" => droneController.tap()
        case "Metronom" => droneController.metronome()
        case "Ondulation" => droneController.ondulation()
        case "Schwanken" => droneController.slowShake()
        case "Slalom" => droneController.slalom()
      }
    }
    Ok
  }

  /**
   * Service called by Scratch to ask which command is currently processed
   * Only supports one command at a time currently. Maybe extended by using a set of movementIds....
   * @return
   */
  def poll () = Action {
    var message = ""
    if (movementId!=0) {
      message= s"_busy $movementId"
      Logger.info (s"poll $message")
    }

    Ok(message)
  }

  /**
   * Service called by Scratch if the programm is stopped by the user.
   * @return
   */
  def reset() = Action {
    Logger.info("resetting DroneConnection and running Command")
    movementId = 0
    if (droneConnection!=null && droneConnection != null) {
      droneController.close()
    }
    Ok
  }

  /**
   * Method that sets up the monitoring of the last command.
   * The way it works is that for specially marked command scratch provides a unique id for the
   * call. As long as the command is being executed this id should be reported back to Scratch
   * when it calls the poll service
   * @param id
   * @param command
   * @return
   */
  def runAndMonitorCommand  (id: Int)(command: => Unit): Unit = {
    movementId = id
    command
    movementId = 0
  }

}