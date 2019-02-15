package org.nlogo.extensions.vid

import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.File
import java.lang.{ Void => JVoid }

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.value.{ ChangeListener, ObservableValue }
import javafx.embed.swing.SwingFXUtils
import javafx.scene.{ Group, Scene, SnapshotResult }
import javafx.scene.media.{ Media, MediaException, MediaPlayer, MediaView }
import javafx.scene.media.MediaPlayer.Status.UNKNOWN
import javafx.util.{ Callback, Duration }

import scala.concurrent.Channel

import util.FunctionToCallback.function2ChangeListener

trait MovieFactory {
  // throws InvalidFormatException when the filePath points to a file
  // whose format cannot be understood
  def open(filePath: String): Option[VideoSource]
  // throws InvalidProtocolException when the uri is not a supported protocol
  def openRemote(uri: String): Option[VideoSource]
}

class InvalidFormatException extends Exception("Invalid file format")

class InvalidProtocolException extends Exception("Invalid protocol")

object Movie extends MovieFactory {
  def open(filePath: String): Option[VideoSource] = {
    val file = new File(filePath)
    if (file.exists) {
      try {
        Some(buildMovie(file.toURI.toString))
      } catch {
        case me: MediaException if me.getMessage == "Unrecognized file signature!" =>
          throw new InvalidFormatException()
      }
    } else
      None
  }

  def openRemote(uri: String): Option[VideoSource] = {
    try {
      val m = buildMovie(uri)
      m.awaitLoad match {
        case None    => Some(m)
        case Some(e) => e.getType match {
          case MediaException.Type.MEDIA_UNSUPPORTED =>
            throw new InvalidFormatException()
          case _ => None
        }
      }
    } catch {
      case e: UnsupportedOperationException if e.getMessage.startsWith("Unsupported protocol") =>
        throw new InvalidProtocolException()
    }
  }

  private def buildMovie(uri: String): Movie = {
    val media = new Media(uri)
    new Movie(media, new MediaPlayer(media))
  }
}

class Movie(media: Media, mediaPlayer: MediaPlayer) extends VideoSource {
  mediaPlayer.setMute(true)

  override def play(): Unit =
    mediaPlayer.play()

  override def stop(): Unit =
    mediaPlayer.pause()

  override def close(): Unit = {
    mediaPlayer.stop()
    mediaPlayer.dispose()
  }

  def awaitLoad(): Option[MediaException] = {
    val openException = new Channel[Option[MediaException]]

    media.setOnError { () =>
      openException.write(Option(media.getError))
    }

    val listener: ChangeListener[MediaPlayer.Status] = {
      function2ChangeListener {
        (oldStatus: MediaPlayer.Status, newStatus: MediaPlayer.Status) =>
          if (newStatus != UNKNOWN)
            openException.write(None)
      }
    }

    mediaPlayer.statusProperty.addListener(listener)

    val returnValue =
      if (mediaPlayer.getStatus != null && mediaPlayer.getStatus != UNKNOWN)
        None
      else
        openException.read

    mediaPlayer.statusProperty.removeListener(listener)
    media.setOnError({ () => })
    returnValue
  }

  override def isPlaying: Boolean =
    mediaPlayer.getStatus == MediaPlayer.Status.PLAYING

  override def captureImage(): BufferedImage = {
    val chan = new Channel[BufferedImage]

    val callback = new Callback[SnapshotResult, JVoid] {
      def call(res: SnapshotResult): JVoid = {
        chan.write(SwingFXUtils.fromFXImage(res.getImage, null))
        null
      }
    }

    Platform.runLater { () =>
      val node = movieNode(None).node
      val scene = new Scene(new Group(node))
      scene.snapshot(callback, null)
    }

    chan.read
  }

  private def movieNode(bounds: Option[(Double, Double)]): BoundedNode = {
    val mediaView = new MediaView(mediaPlayer)
    bounds.foreach {
      case (w, h) =>
        mediaView.setFitWidth(w)
        mediaView.setFitHeight(h)
    }
    val preferredSize: ObservableValue[Dimension] =
      Bindings.createObjectBinding[Dimension](
        () =>
          new Dimension(mediaView.getBoundsInLocal.getWidth.toInt, mediaView.getBoundsInLocal.getHeight.toInt),
          mediaView.boundsInLocalProperty)
    BoundedNode(mediaView, preferredSize, bounds)
  }

  override def setTime(timeInSeconds: Double): Unit = {
    val requestedTime = new Duration(timeInSeconds * 1000)
    if (timeInSeconds < 0 || requestedTime.greaterThan(media.getDuration))
      throw new IllegalArgumentException(s"invalid time $timeInSeconds")
    mediaPlayer.seek(requestedTime)
  }

  def videoNode(bounds: Option[(Double, Double)]): BoundedNode =
    movieNode(bounds)
}
