/*
 * Copyright (C) 2012 Lalit Pant <pant.lalit@gmail.com>
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
package net.kogics.kojo.lite

import java.io.File

import scala.sys.process.stringToProcess

import net.kogics.kojo.util.Utils

trait StubMain {
  def classpath: String
  def firstInstance: Boolean
  def firstMain(args: Array[String]): Unit
  def nthMain(args: Array[String]): Unit

  def main(args: Array[String]): Unit = {
    Utils.safeProcess {
      if (firstInstance) {
        println("First Kojo~Ray Instance Requested...")
        firstMain(args)
        realMain(args)
      }
      else {
        println("Nth Kojo~Ray instance Requested: " + args)
        nthMain(args)
      }
    }
    println("Kojo~Ray Launcher Done.")
    System.exit(0)
  }

  def realMain(args: Array[String]) {
    val javaHome = System.getProperty("java.home")
    println("Java Home: " + javaHome)
    val javaExec = {
      if (new File(javaHome + "/bin/javaw.exe").exists) {
        println("Using javaw")
        javaHome + "/bin/javaw"
      }
      else {
        javaHome + "/bin/java"
      }
    }
    val command = "%s -cp %s -client -Xms32m -Xmx512m " +
      "-Xss1m -XX:PermSize=32m -XX:MaxPermSize=256m -Dapple.laf.useScreenMenuBar=true " +
      "-Dapple.awt.graphics.UseQuartz=true -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled " +
      "-XX:+CMSPermGenSweepingEnabled net.kogics.kojo.lite.Main %s" format (javaExec, classpath, args.mkString(" "))

    println("Starting Real Kojo~Ray...")
    command!
  }

  def createCp(xs: List[String]): String = {
    val ourCp = new StringBuilder
//    Bad stuff on the classpath can clobber the launch of the Real Kojo~Ray     
//    val oldCp = System.getenv("CLASSPATH")
//    if (oldCp != null) {
//      ourCp.append(oldCp)
//      ourCp.append(File.pathSeparatorChar)
//    }

    // allow another way to customize classpath
    val kojoCp = System.getenv("KOJO_CLASSPATH")
    if (kojoCp != null) {
      ourCp.append(kojoCp)
      ourCp.append(File.pathSeparatorChar)
    }

    // add all jars in user's kojo lib dir to classpath
    Utils.libJars.foreach { x =>
      ourCp.append(Utils.libDir)
      ourCp.append(File.separatorChar)
      ourCp.append(x)
      ourCp.append(File.pathSeparatorChar)
    }

    ourCp.append(xs.mkString(File.pathSeparator))
    ourCp.toString
  }
}