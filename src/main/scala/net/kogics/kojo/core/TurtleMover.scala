/*
 * Copyright (C) 2009 Lalit Pant <pant.lalit@gmail.com>
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

import java.awt.Font
import java.awt.Paint
import java.awt.geom.Point2D
import java.awt.Image

case class Style(val penColor: Paint, val penThickness: Double, val fillColor: Paint, val font: Font, down: Boolean)

trait TurtleMover extends RichTurtleCommands {
  def forward(n: Double): Unit
  def forward(): Unit = forward(25)
  def turn(angle: Double): Unit
  def penUp(): Unit
  def penDown(): Unit
  def setPenColor(color: Paint): Unit
  def setPenThickness(t: Double): Unit
  def setFillColor(color: Paint): Unit
  def saveStyle(): Unit
  def restoreStyle(): Unit
  def savePosHe(): Unit
  def restorePosHe(): Unit
  def style: Style
  def towards(x: Double, y: Double): Unit
  def towards(p: Point): Unit = towards(p.x, p.y)
  def position: Point
  def heading: Double
  def direction = heading
  def jumpTo(x: Double, y: Double): Unit
  def jumpTo(p: Point): Unit = jumpTo(p.x, p.y)
  def setPosition(x: Double, y: Double) = jumpTo(x, y)
  def setPosition(p: Point): Unit = jumpTo(p)
  def moveTo(x: Double, y: Double): Unit
  def moveTo(p: Point): Unit = moveTo(p.x, p.y)
  def setHeading(angle: Double) = turn(angle - heading)
  def home() = {
    moveTo(0, 0)
    setHeading(90)
  }
  def animationDelay: Long
  def setAnimationDelay(d: Long)
  def beamsOn(): Unit
  def beamsOff(): Unit
  def write(text: String): Unit
  def write(obj: Any): Unit = write(obj.toString)
  def visible(): Unit
  def invisible(): Unit
  def waitFor(): Unit = animationDelay
  def playSound(voice: Voice): Unit
  def setPenFontSize(n: Int)
  def setPenFont(font: Font)
  def arc2(r: Double, a: Double): Unit
  def circle(r: Double) = arc2(r, 360)
  def setCostumeImage(image: Image): Unit
  def setCostume(costumeFile: String): Unit
  def setCostumes(costumeFiles: Vector[String]): Unit
  def setCostumes(costumeFiles: String*): Unit = setCostumes(costumeFiles.toVector)
  def setCostumeImages(images: Vector[Image]): Unit
  def setCostumeImages(images: Image*): Unit = setCostumeImages(images.toVector)
  def nextCostume(): Unit
  def scaleCostume(factor: Double): Unit
  def changePosition(x: Double, y: Double): Unit
  def hop(n: Double) {
    penUp()
    forward(n)
    penDown()
  }
  def hop(): Unit = hop(25)
  def dot(diameter: Int): Unit = {
    saveStyle()
    setPenThickness(diameter)
    forward(0.01)
    restoreStyle()
  }
  def perimeter: Double
  def area: Double
  def lastLine: Option[(Point2D.Double, Point2D.Double)]
  def lastTurn: Option[(Point2D.Double, Double, Double)]
}
