/*
 * Copyright (C) 2010 Lalit Pant <pant.lalit@gmail.com>
 *
 * The contents of this file are subject to the GNU General Public License
 * Version 3 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.gnu.org/copyleft/gpl.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 */

package net.kogics.kojo
package action

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.event.ActionEvent

import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JFrame

import net.kogics.kojo.core.KojoCtx
import net.kogics.kojo.lite.topc.BaseHolder
import net.kogics.kojo.util.Utils

import FullScreenSupport.sdev
import lite.CodeExecutionSupport

class ChooseColor(ctx: KojoCtx) extends AbstractAction(Utils.loadString("S_ChooseColor")) {
  def actionPerformed(e: ActionEvent) {
    val sColor = JColorChooser.showDialog(null, util.Utils.stripDots(e.getActionCommand), ctx.lastColor)
    if (sColor != null) {
      val cprint = CodeExecutionSupport.instance.showOutput(_: String, _: Color)
      cprint("\u2500" * 3 + "\n", sColor)
      print("Selected Color:   ")
      cprint("\u2588" * 6 + "\n", sColor)
      val color = if (sColor.getAlpha < 255) {
        "Color(%d, %d, %d, %d)" format (sColor.getRed, sColor.getGreen, sColor.getBlue, sColor.getAlpha)
      }
      else {
        "Color(%d, %d, %d)" format (sColor.getRed, sColor.getGreen, sColor.getBlue)
      }
      println(color)
      println("Example usage: setPenColor(%s)" format (color))
      cprint("\u2500" * 3 + "\n", sColor)
      ctx.lastColor = sColor
    }
  }
}

object CloseFile {
  var action: Action = _
  def onFileOpen() {
    action.setEnabled(true)
  }
  def onFileClose() {
    action.setEnabled(false)
  }
}

class CloseFile
  extends AbstractAction(Utils.loadString("S_Close"), Utils.loadIcon("/images/extra/close.gif")) {
  setEnabled(false)
  CloseFile.action = this

  def actionPerformed(e: ActionEvent) {
    try {
      CodeExecutionSupport.instance.closeFileAndClrEditor()
    }
    catch {
      case e: RuntimeException => // user cancelled
    }
  }
}

class NewFile(ctx: KojoCtx)
  extends AbstractAction(Utils.loadString("S_New"), Utils.loadIcon("/images/extra/new.gif")) {

  val saveAs = new SaveAs(ctx)

  def actionPerformed(e: ActionEvent) {
    try {
      CodeExecutionSupport.instance.closeFileAndClrEditor()
      saveAs.actionPerformed(e);
    }
    catch {
      case e: RuntimeException => // user cancelled
    }
  }
}

object FullScreenSupport {
  lazy val sdev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
  def isFullScreenOn = sdev.getFullScreenWindow != null
}

class FullScreenBaseAction(kojoCtx: => KojoCtx, key: String, fsComp: => JComponent, fsCompHolder: => BaseHolder)
  extends AbstractAction(key) {
  import FullScreenSupport._
  var frame: JFrame = _
  var fullScreen = false

  def isFullScreen = fullScreen

  def enterFullScreen() {
    fullScreen = true
    frame = new JFrame
    frame.setUndecorated(true)
    frame.getContentPane.add(fsComp)
    sdev.setFullScreenWindow(frame)
    frame.validate()
  }

  def leaveFullScreen() {
    fullScreen = false
    sdev.setFullScreenWindow(null)
    frame.setVisible(false)
    fsCompHolder.add(fsComp)
    fsComp.revalidate()
  }

  def actionPerformed(e: ActionEvent) {
    if (!isFullScreen) {
      if (!FullScreenSupport.isFullScreenOn) {
        enterFullScreen()
      }
    }
    else {
      leaveFullScreen()
    }
  }
}

object FullScreenCanvasAction {
  var instance: FullScreenCanvasAction = _
  def apply(kojoCtx: => KojoCtx) = {
    if (instance == null) {
      instance = new FullScreenCanvasAction(kojoCtx)
    }
    instance
  }
}

class FullScreenCanvasAction(kojoCtx: => KojoCtx)
  extends FullScreenBaseAction(
    kojoCtx,
    Utils.loadString("S_FullScreenCanvas"),
    kojoCtx.topcs.dch.dc,
    kojoCtx.topcs.dch
  ) {
  override def enterFullScreen() {
    super.enterFullScreen()
    kojoCtx.activateDrawingCanvas()
  }
}

object FullScreenOutputAction {
  var instance: FullScreenOutputAction = _
  def apply(kojoCtx: => KojoCtx) = {
    if (instance == null) {
      instance = new FullScreenOutputAction(kojoCtx)
    }
    instance
  }
}

class FullScreenOutputAction(kojoCtx: => KojoCtx)
  extends FullScreenBaseAction(
    kojoCtx,
    Utils.loadString("S_FullScreenOutput"),
    kojoCtx.topcs.owh.oPanel,
    kojoCtx.topcs.owh
  ) {
  override def enterFullScreen() {
    super.enterFullScreen()
    kojoCtx.activateOutputPane()
  }
}
