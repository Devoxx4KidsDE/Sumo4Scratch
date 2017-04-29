package controllers


import de.devoxx4kids.dronecontroller.DroneController
import de.devoxx4kids.dronecontroller.network.{DroneConnection, WirelessLanDroneConnection}
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

  var droneConnection: DroneConnection = _
  var droneController: DroneController = _

  var initialized = false

  val maxPhotos: Int = 9
  var currentPhoto = 0

  var photos: Array[Array[Byte]] = Array.ofDim[Byte](maxPhotos, 2)
  for (a <- 0 until maxPhotos) {
    photos(a) = new Array[Byte](0)
  }

  def index = Action {
    Ok(views.html.monitor())
  }

  def monitor = Action {
    Ok(views.html.monitor())
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

  def sumoConnect(ip: String, port: Int) = Action {
    Logger.info(s"Connecting: $ip : $port at $wlan")

    if (droneController != null) {
      droneController.video.disableVideo
      droneController.close()
    }
    try {
      // withoutQueue, so commands become synchronous
      droneConnection = new WirelessLanDroneConnection(ip, port, wlan)
      droneController = new DroneController(droneConnection)
      initialized = true
    } catch {
      case e: Exception => ServiceUnavailable
    }
    Ok
  }

  def sumoClose = Action {
    Logger.info(s"Closing connection: $ip : $port at $wlan")
    if (droneConnection != null && droneController != null) {
      droneController.video.disableVideo
      droneController.close()
    }
    Ok
  }

  /**
    * Allows moving the drone by speed and time.
    * Note: time has only the granularity of 500ms as one forward command runs the drone roughly 500ms
    *
    * @param id    Scratch command id
    * @param speed as number from 10 to ~100
    * @param time  in ms but has a granularity of 500ms
    * @return
    */
  def forward(id: Int, speed: Int, time: Int) = Action {
    Logger.info(s"forward ($id / $speed / $time)")

    runAndMonitorCommand(id) {
      var runtime = 0L
      var start = System.currentTimeMillis()
      droneController.pcmd(speed, 0, time)
    }
    Ok
  }

  def backward(id: Int, speed: Int, time: Int) = Action {
    Logger.info(s"backward ($id / $speed / $time)")

    runAndMonitorCommand(id) {
      droneController.pcmd(-speed, 0, time)
    }
    Ok
  }

  def left(id: Int, degrees: Int) = Action {
    Logger.info(s"left $degrees")

    runAndMonitorCommand(id) {
      droneController.left(degrees)
    }
    Ok
  }

  def right(id: Int, degrees: Int) = Action {
    Logger.info(s"right $degrees")

    runAndMonitorCommand(id) {
      droneController.right(degrees)
    }
    Ok
  }

  def jump(id: Int, jumpType: String) = Action {
    Logger.info(s"jump $jumpType")

    runAndMonitorCommand(id) {
      jumpType match {
        case "hoch" | "High" => droneController.jumpHigh
        case "weit" | "Far" => droneController.jumpLong
      }
    }
    Ok
  }

  def trick(id: Int, trick: String) = Action {
    Logger.info(s"Do trick $trick")

    runAndMonitorCommand(id) {
      trick match {
        case "Drehung" | "Spin" => droneController.spin
        case "Drehsprung" | "JumpAndSpin" => droneController.spinJump
        case "Tippen" | "Tap" => droneController.tap
        case "Metronom" | "Metronome" => droneController.metronome
        case "Ondulation" | "Ondulation" => droneController.ondulation
        case "Schwanken" | "Shake" => droneController.slowShake
        case "Slalom" => droneController.slalom
      }
    }
    Ok
  }

  def video(id: Int, switch: String) = Action {
    Logger.info(s"video $switch")

    runAndMonitorCommand(id) {
      switch match {
        case "an" | "on" => droneController.video.enableVideo
        case "aus" | "off" => droneController.video.disableVideo
      }
    }
    Ok
  }


  def takePhoto(id: Int) = Action {
    Logger.info(s"taking photo $currentPhoto")

    runAndMonitorCommand(id) {
      if (droneController != null && droneController.video() != null) {
        val data = droneController.video.getLastJpg
        val imageLength: Int = data.length
        photos(currentPhoto) = new Array[Byte](imageLength)
        System.arraycopy(data, 0, photos(currentPhoto), 0, imageLength)
        currentPhoto = currentPhoto + 1
        if (currentPhoto >= maxPhotos)
          currentPhoto = 0
      }
    }
    Ok
  }


  /**
    * Service called by Scratch to ask which command is currently processed
    * Only supports one command at a time.
    *
    * @return
    */
  def poll() = Action {
    var message = ""

    if (movementId != 0) {
      message = s"_busy $movementId"
      Logger.info(s"poll $message")
    }
    if (droneController != null) {
      message = message + "\nbatterylevel " + droneController.getBatteryLevel
    }

    Ok(message)
  }

  /**
    * Service called by Scratch if the program is stopped by the user.
    *
    * @return
    */
  def reset() = Action {
    Logger.info("resetting DroneConnection and running Command")

    movementId = 0
    if (droneConnection != null && droneController != null) {
      droneController.video.disableVideo
      droneController.close()
    }
    Ok
  }

  /**
    * Method that sets up the monitoring of the last command.
    * The way it works is that for specially marked command scratch provides a unique id for the
    * call. As long as the command is being executed this id should be reported back to Scratch
    * when it calls the poll service
    *
    * @param id      unique id of a sent command to keep track of it that is still running
    * @param command command that should be executed
    * @return
    */
  def runAndMonitorCommand(id: Int)(command: => Unit): Unit = {
    if (!initialized)
      return

    movementId = id
    command
    movementId = 0
  }

  /*
      These are the actions that are supposed to be called by the web page
   */

  /**
    * Return the latest JPG of the videostream
    *
    * @return
    */
  def getPhoto(id: Int) = Action {
    Ok(photos(id))
  }

  def getVideoFrame = Action {

    if (droneController == null || droneController.video() == null) {
      NotFound
    } else {
      Ok(droneController.video.getLastJpg).as("image/jpeg")
    }
  }

  def isFrameAvailable = Action {

    if (droneController == null || droneController.video() == null)
      Ok("no")
    else if (droneController.video.getLastJpg.length == 0)
      Ok("no")
    else
      Ok("yes")
  }

  def isVideoOn = Action {

    if (droneController == null || droneController.video() == null)
      Ok("no")
    else if (droneController.video.isVideoEnabled)
      Ok("yes")
    else
      Ok("no")
  }

  /**
    * Return the battery level
    *
    * @return
    */
  def getBatteryLevel = Action {

    if (droneController != null) {
      Logger.info("" + droneController.getBatteryLevel)
      Ok("" + droneController.getBatteryLevel)
    } else
      Ok("unbekannt")
  }
}