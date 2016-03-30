package controllers

import de.bischinger.parrot.control.DroneController
import de.bischinger.parrot.lib.command.movement.Pcmd
import de.bischinger.parrot.lib.network.{WirelessLanDroneConnection, DroneConnection}

import play.api._
import play.api.mvc._

/**
 * This controller serves as the main "proxy" to receive Scratch's calls and routes them
 * to the respective methods of the drone controller.
 *
 * It also takes care that if a command is started there is a reporting back to Scratch as long as
 * the command is still running. Note that for simplicity currently only one command at a time is
 * allowed to be executed however this could be easily extend by not using movementId but a set of ids
 *
 * The following is based on Scratch' extension mechanism which has been documented here:
 * http://wiki.scratch.mit.edu/wiki/Scratch_Extension
 * but in particalur in the the following document:
 * http://wiki.scratch.mit.edu/w/images/ExtensionsDoc.HTTP-9-11.pdf
 *
 */

object Application extends Controller {

  def ip = "192.168.2.1"
  def port = 44444
  def wlan = "my wireless lan" // not sure yet what it is for. Tobias?

  var movementId: Int = 0

  var droneConnection: DroneConnection = null
  var droneController: DroneController = null

  var initialized = false

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
      initialized = true
      OK
    } catch {
      case e: Exception => ServiceUnavailable
    }
    Ok
  }

  def sumoClose = Action {
    Logger.info(s"Closing connection: $ip : $port at $wlan")
    if (droneConnection!=null && droneController != null) {
      droneController.close()
    }
    Ok
  }

  /**
   * Allows moving the drone by speed and time.
   * Note: time has only the granularity of 500ms as one forward command runs the drone roughly 500ms
   * @param id Scratch command id
   * @param speed as number from 10 to ~100
   * @param time in ms but has a granularity of 500ms
   * @return
   */
  def forward (id: Int, speed: Int, time: Int) = Action {
    Logger.info(s"forward ($id / $speed / $time)")
    runAndMonitorCommand (id) {
      var runtime = 0L
      var start = System.currentTimeMillis()
      while (runtime < time) {
        droneController.pcmd(speed,0)
        Thread.sleep(500)
        runtime = System.currentTimeMillis() - start
      }
    }

    Ok

  }

  def backward (id: Int, speed: Int, time: Int)  = Action {
    Logger.info(s"backward ($id / $speed / $time)")
    runAndMonitorCommand (id) {
      var runtime = 0L
      var start = System.currentTimeMillis()
      while (runtime < time) {
        droneController.pcmd(-speed,0)
        Thread.sleep(500)
        runtime = System.currentTimeMillis() - start
      }
    }
    Ok
  }

  def left (id: Int, degrees: Int) = Action {
    Logger.info("left $degrees")
    runAndMonitorCommand (id) {
        droneController.left(degrees)
    }
    Ok
  }

  def right (id: Int, degrees: Int) = Action {
    Logger.info("right $degrees")
    runAndMonitorCommand (id) {
      droneController.right(degrees)
    }
    Ok
  }

  def jump (id: Int, jumpType:String) = Action {
    Logger.info(s"jump $jumpType")

    runAndMonitorCommand (id) {
      jumpType match {
        case "hoch" | "High" => droneController.jumpHigh()
        case "weit" | "Far" => droneController.jumpLong()
      }
    }
    Ok
  }

  def trick (id: Int, trick:String) = Action {
    Logger.info(s"Do trick $trick")

    runAndMonitorCommand (id) {
      trick match {
        case "Drehung" | "Spin" => droneController.spin()
        case "Drehsprung"| "JumpAndSpin" => droneController.spinJump()
        case "Tippen" | "Tap" => droneController.tap()
        case "Metronom" | "Metronome" => droneController.metronome()
        case "Ondulation" | "Ondulation" => droneController.ondulation()
        case "Schwanken" | "Shake" => droneController.slowShake()
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
    if (droneConnection!=null && droneController != null) {
      droneController.close()
    }
    Ok
  }

  /**
   * Method that sets up the monitoring of the last command.
   * The way it works is that for specially marked command scratch provides a unique id for the
   * call. As long as the command is being executed this id should be reported back to Scratch
   * when it calls the poll service
   * @param id unique id of a sent command to keep track of it that is still running
   * @param command command that should be executed
   * @return
   */
  def runAndMonitorCommand  (id: Int)(command: => Unit): Unit = {
    if (!initialized)
      return
    movementId = id
    command
    movementId = 0
  }

}