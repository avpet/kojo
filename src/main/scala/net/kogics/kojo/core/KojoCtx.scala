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

package net.kogics.kojo.core

import javax.swing.JFrame
import java.awt.Color
import net.kogics.kojo.lite.TopCs
import net.kogics.kojo.lite.CodeExecutionSupport

trait KojoCtx {
  def topcs: TopCs
  def activityListener: SpriteListener
  def codeSupport: CodeExecutionSupport
  def activateDrawingCanvasHolder()
  def activateDrawingCanvas()
  def activateScriptEditor()
  def activateOutputPane()
  def makeStagingVisible()
  def makeTurtleWorldVisible()
  def makeMathWorldVisible()
  def makeStoryTellerVisible()
  def make3DCanvasVisible()
  def baseDir: String
  def stopInterpreter(): Unit
  def stopAnimation(): Unit
  def stopStory(): Unit
  def scrollOutputToEnd(): Unit
  def frame: JFrame
  def fileOpened(file: java.io.File): Unit
  def fileModified(): Unit
  def fileSaved(): Unit
  def fileClosed(): Unit
  def getLastLoadStoreDir(): String
  def setLastLoadStoreDir(dir: String): Unit
  def saveAsFile(): Unit
  def drawingCanvasActivated(): Unit
  def mwActivated(): Unit
  def d3Activated(): Unit
  def lastColor: Color
  def lastColor_=(c: Color)
  def knownColors: List[String]
  def isVerboseOutput: Boolean
  def showVerboseOutput(): Unit
  def hideVerboseOutput(): Unit
  def isSriptShownInOutput: Boolean
  def showScriptInOutput(): Unit
  def hideScriptInOutput(): Unit
  def clearOutput(): Unit
  def userLanguage: String
  def userLanguage_=(lang: String)
  def switchToDefaultPerspective()
  def switchToScriptEditingPerspective()
  def switchToWorksheetPerspective()
  def switchToStoryViewingPerspective()
  def switchToHistoryBrowsingPerspective()
  def switchToCanvasPerspective()
  def setOutputBackground(color: Color)
  def setOutputForeground(color: Color)
  def setOutputFontSize(size: Int)
  def formatSource(): Unit
  var fps: Int
}
