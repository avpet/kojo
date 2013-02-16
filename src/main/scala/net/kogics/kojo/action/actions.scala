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
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JCheckBoxMenuItem
import javax.swing.JColorChooser
import javax.swing.JComponent
import javax.swing.JFrame

import net.kogics.kojo.core.KojoCtx
import net.kogics.kojo.lite.EditorFileSupport
import net.kogics.kojo.lite.topc.BaseHolder
import net.kogics.kojo.lite.topc.DrawingCanvasHolder
import net.kogics.kojo.lite.topc.OutputWindowHolder
import net.kogics.kojo.util.Utils

import FullScreenSupport.sdev
import core.CodeExecutionSupport

class ChooseColor(execSupport: CodeExecutionSupport) extends AbstractAction(Utils.loadString("S_ChooseColor")) {
  val ctx = execSupport.kojoCtx
  def actionPerformed(e: ActionEvent) {
    val sColor = JColorChooser.showDialog(null, util.Utils.stripDots(e.getActionCommand), ctx.lastColor)
    if (sColor != null) {
      val cprint = execSupport.showOutput(_: String, _: Color)
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

class CloseFile(fileSupport: EditorFileSupport)
  extends AbstractAction(Utils.loadString("S_Close"), Utils.loadIcon("/images/extra/close.gif")) {
  setEnabled(false)
  CloseFile.action = this

  def actionPerformed(e: ActionEvent) {
    try {
      fileSupport.closeFileAndClrEditor()
    }
    catch {
      case e: RuntimeException => // user cancelled
    }
  }
}

class NewFile(fileSupport: EditorFileSupport)
  extends AbstractAction(Utils.loadString("S_New"), Utils.loadIcon("/images/extra/new.gif")) {

  val saveAs = new SaveAs(fileSupport)

  def actionPerformed(e: ActionEvent) {
    try {
      fileSupport.closeFileAndClrEditor()
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

  def updateMenuItem(mi: JCheckBoxMenuItem, action: FullScreenBaseAction) {
    if (isFullScreenOn) {
      if (action.isFullScreen) {
        mi.setState(true)
        mi.setEnabled(true)
      }
      else {
        mi.setState(false)
        mi.setEnabled(false)
      }
    }
    else {
      mi.setState(false)
      mi.setEnabled(true)
    }
  }
}

class FullScreenBaseAction(key: String, fsComp: => JComponent, fsCompHolder: => BaseHolder)
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

    val escComp = frame.getMostRecentFocusOwner()
    if (escComp != null) {
      escComp.addKeyListener(new KeyAdapter {
        override def keyPressed(event: KeyEvent) {
          if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            escComp.removeKeyListener(this)
            leaveFullScreen()
          }
        }
      })
    }
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
  def apply(dch: => DrawingCanvasHolder, kojoCtx: KojoCtx) = {
    if (instance == null) {
      instance = new FullScreenCanvasAction(dch, kojoCtx)
    }
    instance
  }
}

class FullScreenCanvasAction(dch: => DrawingCanvasHolder, kojoCtx: KojoCtx)
  extends FullScreenBaseAction(
    Utils.loadString("S_FullScreenCanvas"),
    dch.dc,
    dch
  ) {
  override def enterFullScreen() {
    dch.dc.setFocusable(true) // make canvas work with frame.getMostRecentFocusOwner()
    super.enterFullScreen()
    kojoCtx.activateDrawingCanvas()
  }
}

object FullScreenOutputAction {
  var instance: FullScreenOutputAction = _
  def apply(owh: => OutputWindowHolder) = {
    if (instance == null) {
      instance = new FullScreenOutputAction(owh)
    }
    instance
  }
}

class FullScreenOutputAction(owh: => OutputWindowHolder)
  extends FullScreenBaseAction(
    Utils.loadString("S_FullScreenOutput"),
    owh.outputPane,
    owh
  ) {
  override def enterFullScreen() {
    super.enterFullScreen()
  }
}
