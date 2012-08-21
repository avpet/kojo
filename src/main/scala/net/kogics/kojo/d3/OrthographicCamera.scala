/*
 * Copyright (C) 2012 Jerzy Redlarski <5xinef@gmail.com>
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

package net.kogics.kojo.d3

import java.lang.Math._
import java.awt.image.BufferedImage
import java.awt.Color

class OrthographicCamera(position : Vector3d = Vector3d(0d, 0d, 10d),
             orientation : Quaternion4d = Quaternion4d(),
             width : Int = Defaults.cameraWidth,
             height : Int = Defaults.cameraHeight,
             axesVisible : Boolean = Defaults.axesVisible,
             defaultLightsOn : Boolean = Defaults.defaultLightsOn,
             frequency : Int = Defaults.frequency) extends Camera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency) {
  
  def setPosition(position : Vector3d) =
    new OrthographicCamera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency)
  def setOrientation(orientation : Quaternion4d) =
    new OrthographicCamera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency)
  def setPictureDimensions(width : Int, height : Int) =
    new OrthographicCamera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency)
  def setAngle(angle : Double) =
    new OrthographicCamera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency)
  def setAxesVisibility(axesVisible : Boolean) =
    new OrthographicCamera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency)
  def setFrequency(frequency : Int) =
    new OrthographicCamera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency)
  def setDefaultLights(defaultLightsOn : Boolean) =
    new OrthographicCamera(position, orientation, width, height, axesVisible, defaultLightsOn, frequency)

  override def render(shapes : List[Shape], lights : List[Light], turtle : Turtle3d) = {
    
    val buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g2d = buffer.getGraphics()
    val xRange = Canvas3D.instance().image.getWidth()
    val yRange = Canvas3D.instance().image.getHeight()
    
    for (row <- 0 to height) {
      for (column <- 0 to width) {

        val x = (1.0d - (column / (width / 2d))) * (xRange toDouble) / 50d
        val y = (1.0d - (row / (height / 2d))) * (yRange toDouble) / 50d
        
        val rayOrigin = (-orientation).rotate(Vector3d(x, y, 0)) + position

        val ray = new Ray(rayOrigin,
            (-orientation).rotate(Vector3d(0, 0, 1)))
          
        val allShapes = {
          val withTurtle = if(turtle.visible) {
            turtle.avatar ::: shapes
          }
          else
            shapes
          
          if(axesVisible) {
            Axes.avatarWithTicks ::: withTurtle
          }
          else
            withTurtle
        }
        
        val allLights = {
          if(defaultLightsOn)
            DefaultLights.lights ::: lights
          else
            lights
        }
        
        val (distance, closestShape) = allShapes.foldLeft(
          (Double.MaxValue, None : Option[Shape]))(
          (result, shape) => shape.intersection(ray) match {
            case Some(t) => {
                if(t < result._1) (t, Option(shape))
                else result
            }
            case None => result
          }
        )

        val color = closestShape match {
          case Some(t) => {
            val point = ray.origin + ray.direction * distance
            t.shade(point, allLights)
          }
          case None => {
            new Vector3d(0.4d, 0.5d, 0.6d)
          }
        }
        g2d.setColor(new Color(clip(color.x), clip(color.y), clip(color.z)))
        g2d.drawLine(column, row, column, row)
      }
    }
    g2d.dispose()
    buffer
  }
}