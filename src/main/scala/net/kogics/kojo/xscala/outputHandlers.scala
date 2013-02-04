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
package xscala

import core._
import java.util.logging._

class InterpOutputHandler(ctx: RunContext) {
  val Log = Logger.getLogger(getClass.getName);

  @volatile var errorSeen = false

  val OutputMode = 1
  //  val ErrorMsgMode = 2
  val ErrorTextMode = 3
  val HatMode = 4
  val ErrorTextWithoutLinkMode = 5
  @volatile var currMode = OutputMode

  val errorPattern = java.util.regex.Pattern.compile("""(^<console>:\d+: )error:""")
  val exceptionPattern = java.util.regex.Pattern.compile("""^\w+(\.[\w\$]+)+(Exception|Error)""")
  @volatile var interpOutputSuppressed = false
  @volatile var worksheetLineNum: Option[Int] = None

  def showInterpOutput(lineFragment: String) {
    if (!interpOutputSuppressed) reportInterpOutput(lineFragment)
  }

  private def reportExceptionOutput(output0: String) {
    Log.info("Exception in interpreter output: " + output0)
    val lines = output0.split("\n")

    val output = if (lines.size > 5) {
      lines.take(5).mkString("\n") + "......\n"
    }
    else {
      output0
    }
    ctx.kprintln(output)
  }

  private def reportNonExceptionOutput(output: String) {
    val m = errorPattern.matcher(output)
    if (m.find) {
      ctx.reportErrorMsg(output.substring(m.group(1).length, output.length))
    }
    else {
      ctx.reportOutput(output)
    }
  }

  def reportInterpOutput(output: String) {
    if (output == "") return

    worksheetLineNum foreach { ctx.reportWorksheetOutput(output, _) }

    if (exceptionPattern.matcher(output).find) {
      reportExceptionOutput(output)
    }
    else {
      reportNonExceptionOutput(output)
    }
  }

  def withOutputSuppressed[T](fn: => T): T = {
    interpOutputSuppressed = true
    var ret: T = null.asInstanceOf[T]
    try {
      ret = fn
    }
    finally {
      interpOutputSuppressed = false
    }
    ret
  }
}

class CompilerOutputHandler(ctx: RunContext) extends CompilerListener {
  def error(msg: String, line: Int, column: Int, offset: Int, lineContent: String) {
    ctx.reportErrorMsg("Error[%d,%d]: %s\n" format (line, column, msg))
    ctx.reportSmartErrorText("%s\n" format (lineContent), line, column, offset)
    ctx.reportErrorMsg(" " * (column - 1) + "^\n")
  }

  def warning(msg: String, line: Int, column: Int) {
    ctx.kprintln("Warning: %s\n" format (msg))
  }

  def info(msg: String, line: Int, column: Int) {
    ctx.kprintln("Info: %s\n" format (msg))
  }

  def message(msg: String) {
    ctx.kprintln("%s\n" format (msg))
  }
}