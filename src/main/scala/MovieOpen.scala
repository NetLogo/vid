package org.nlogo.extensions.vid

import org.nlogo.api._

class MovieOpen(vid: VideoSourceContainer, files: MovieFactory) extends DefaultCommand {
  override def getSyntax = Syntax.commandSyntax(Array[Int](Syntax.StringType))

  def perform(args: Array[Argument], context: Context): Unit = {
    val filePath = context.attachCurrentDirectory(args(0).getString)
    try {
      vid.videoSource = files.open(filePath)
      if (vid.videoSource.isEmpty)
        throw new ExtensionException("vid: no movie found")
    } catch {
      case e: InvalidFormatException =>
        throw new ExtensionException("vid: format not supported")
    }
  }
}
