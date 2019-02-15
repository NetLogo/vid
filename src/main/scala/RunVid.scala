package org.nlogo.extensions.vid

import javafx.application.Application
import javafx.concurrent.Task
import javafx.scene.Scene
import javafx.scene.control.ButtonBuilder
import javafx.scene.layout.VBox
import javafx.stage.Stage

import org.nlogo.api.Argument

import scala.language.existentials

object RunVid extends App with VideoSourceContainer {

  val movies: MovieFactory   = Movie
  val cameras: CameraFactory = Camera
  val recorder: Recorder     = new MP4Recorder()
  lazy val player: Player    = new JavaFXPlayer()

  val context = new util.CurrentDirContext()

  lazy val commandOptions = Map[String, () => Unit](
    "Show Player"  ->
    { () => new ShowPlayer(player, this).perform(Array[Argument](), context) },
    "Show Small Player" ->
    { () => new ShowPlayer(player, this).perform(Array[Argument](new util.FakeArgument(Double.box(100)), new util.FakeArgument(Double.box(100))), context) },
    "hidePlayer"  ->
    { () => new HidePlayer(player).perform(Array[Argument](), context) },
    "openMovie"   ->
    { () => new MovieOpen(this, movies).perform(Array[Argument](new util.FakeArgument("src/test/resources/small.mp4")), context) },
    "openCamera"  ->
    { () => new CameraOpen(this, cameras).perform(Array[Argument](), context) },
    "closeSource" ->
    { () => new CloseVideoSource(this).perform(Array[Argument](), context) },
    "Start Recorder" ->
    { () => new StartRecorder(recorder).perform(Array[Argument](), context) },
    "Record Source" ->
    { () => new RecordSource(recorder, this).perform(Array[Argument](), context) },
    "Save Recording" ->
    { () => new SaveRecording(recorder).perform(Array[Argument](new util.FakeArgument("testrecording.mp4")), context) }
    )

  class MyApp extends Application {
    import javafx.event.{ ActionEvent, EventHandler }
    override def start(stage: Stage): Unit = {
      val buttons = commandOptions.map {
        case (name, actionThunk) =>
          val bb = ButtonBuilder.create()
          bb.text(name)
            .onAction(new EventHandler[ActionEvent] {
              def handle(ev: ActionEvent): Unit = {
                val task = new Task[Unit] {
                  override def call(): Unit = {
                    actionThunk()
                  }
                }
                val th = new Thread(task)
                th.setDaemon(true)
                th.start()
              }
            })
            .build()
      }.toSeq

      val vbox = new VBox()
      vbox.getChildren.addAll(buttons: _*)
      stage.setScene(new Scene(vbox))
      stage.show()
    }
  }

  var _videoSource: Option[VideoSource] = None

  def videoSource: Option[VideoSource] = _videoSource

  def videoSource_=(source: Option[VideoSource]): Unit = {
    try {
      if (player.isShowing) {
        val videoNode = source.map(n => s => n.videoNode(s))
          .getOrElse(player.emptyNode(_))
          .apply(player.boundedSize)
        player.present(videoNode)
      }
      _videoSource.foreach(_.close())
    } catch {
      case e: Exception =>
        println("VID Extension Exception")
        println(e.getMessage)
        e.printStackTrace()
    }
    _videoSource = source
  }

  Application.launch(classOf[MyApp])
}
