/*
 * Copyright (C) 2013 "Sami Jaber" <jabersami@gmail.com>
 * Copyright (C) 2013 Lalit Pant <pant.lalit@gmail.com>
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
package lite
package trace

import java.awt.Color
import java.awt.geom.Point2D
import java.io.File
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaIterator
import scala.collection.mutable.HashMap
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.Position
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.Reporter
import scala.util.control.Breaks.break
import scala.util.control.Breaks.breakable
import scala.util.matching.Regex
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ArrayReference
import com.sun.jdi.Bootstrap
import com.sun.jdi.IntegerValue
import com.sun.jdi.InvocationException
import com.sun.jdi.LocalVariable
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.StackFrame
import com.sun.jdi.StringReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value
import com.sun.jdi.VirtualMachine
import com.sun.jdi.connect.LaunchingConnector
import com.sun.jdi.event.BreakpointEvent
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.ExceptionEvent
import com.sun.jdi.event.MethodEntryEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.event.ThreadStartEvent
import com.sun.jdi.event.VMDeathEvent
import com.sun.jdi.event.VMDisconnectEvent
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.EventRequest
import net.kogics.kojo.core.Picture
import net.kogics.kojo.core.RunContext
import net.kogics.kojo.core.Turtle
import net.kogics.kojo.core.TwMode
import net.kogics.kojo.lite.Builtins
import net.kogics.kojo.lite.ScriptEditor
import net.kogics.kojo.picture.Pic
import net.kogics.kojo.util.Utils
import net.kogics.kojo.xscala.CompilerOutputHandler
import scala.collection.mutable.ArrayBuffer

class Tracing(scriptEditor: ScriptEditor, builtins: Builtins, traceListener: TraceListener, runCtx: RunContext) {
  @volatile var currThread: ThreadReference = _
  val tmpdir = System.getProperty("java.io.tmpdir")
  val settings = makeSettings()
  val turtles = new HashMap[Long, Turtle]
  val pictures = new HashMap[Long, Picture]
  @volatile var evtReqs: Vector[EventRequest] = _
  @volatile var hiddenEventCount = 0
  @volatile var codeLines: Vector[String] = _
  @volatile var vmRunning = false
  @volatile var verbose = false

  val currEvtVec = new HashMap[String, MethodEvent]

  val listener = new CompilerOutputHandler(runCtx)

  val reporter = new Reporter {
    override def info0(position: Position, msg: String, severity: Severity, force: Boolean) {
      severity.count += 1
      lazy val line = position.line - lineNumOffset
      lazy val offset = position.startOrPoint - offsetDelta
      severity match {
        case ERROR if position.isDefined =>
          listener.error(msg, line, position.column, offset, position.lineContent)
        case WARNING if position.isDefined =>
          listener.warning(msg, line, position.column)
        case INFO if position.isDefined =>
          listener.info(msg, line, position.column)
        case _ =>
          listener.message(msg)
      }
    }
  }
  val compiler = new Global(settings, reporter)
  val tracingGUI = new TracingGUI(scriptEditor, builtins.kojoCtx)

  val prefix0 = """object Wrapper {
val builtins = net.kogics.kojo.lite.trace.TracingBuiltins
import builtins._
import TSCanvas._
import turtle0.{ clear => _, _ }
net.kogics.kojo.lite.i18n.LangInit()
object UserCode {
  def entry() {
    // noop
  }
"""

  val prefix = "%s%s\n" format (prefix0, Utils.initCode(TwMode).getOrElse(""))
  val prefixLines = prefix.lines.size
  @volatile var includedLines = 0
  def lineNumOffset = prefixLines + includedLines
  @volatile var offsetDelta = 0

  val codeTemplate = """%s%s
}
    
def _main() {
    UserCode.entry()
}
def main(args: Array[String]) {
  _main()
}
}
"""

  def stop() {
    traceListener.onEnd()
    if (vmRunning) {
      currThread.virtualMachine.exit(1)
    }
  }

  def compile(code00: String) = {
    val (code0, inclLines, includedChars) = Utils.preProcessInclude(code00)
    includedLines = inclLines
    offsetDelta = prefix.length + includedChars
    val code = codeTemplate format (prefix, code0)

    //    println(s"Tracing Code:\n$code\n---")

    val codeFile = new BatchSourceFile("scripteditor", code)
    val run = new compiler.Run
    reporter.reset
    run.compileSources(List(codeFile))
    if (reporter.hasErrors) {
      runCtx.onCompileError()
      // throw exception to stop trace
      throw new RuntimeException("Trace Compilation Error. Ensure that your program compiles correctly before trying to trace it.")
    }
  }

  def makeSettings() = {
    val iSettings = new Settings()
    iSettings.usejavacp.value = true
    iSettings.outputDirs.setSingleOutput(tmpdir)
    iSettings.g.value = "notailcalls"
    iSettings
  }

  def launchVM() = {
    val conns = Bootstrap.virtualMachineManager.allConnectors
    val connector = conns.find(_.name.equals("com.sun.jdi.RawCommandLineLaunch")).get.asInstanceOf[LaunchingConnector]

    // set connector arguments
    val connArgs = connector.defaultArguments()
    val command = connArgs.get("command")
    if (command == null)
      throw new Error("Bad launching connector")

    val port = 8001 + builtins.random(1000)

    val cmdLine = if (System.getProperty("os.name").contains("Windows"))
      s"""-Xrunjdwp:transport=dt_shmem,address=127.0.0.1:$port,suspend=y -classpath "$tmpdir${File.pathSeparator}${System.getProperty("java.class.path")}" -client -Xms32m -Xmx768m -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled Wrapper"""
    else
      s"""-Xrunjdwp:transport=dt_socket,address=127.0.0.1:$port,suspend=y -classpath "$tmpdir${File.pathSeparator}${System.getProperty("java.class.path")}" -client -Xms32m -Xmx768m -XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled Wrapper"""

    val javaHome = System.getProperty("java.home")
    val javaExec =
      if (new File(javaHome + "/bin/javaw.exe").exists) {
        javaHome + "/bin/javaw"
      }
      else {
        javaHome + "/bin/java"
      }

    command.setValue(s""""$javaExec" $cmdLine""")

    //    println(s"command: $command")

    val address = connArgs.get("address")
    address.setValue(s"127.0.0.1:$port")

    val vm = connector.launch(connArgs)
    vm
  }

  val ignoreMethods = Set("main", "_main", "<clinit>", "$init$")
  val notSupported = Set("PicShape", "animate", "Story", "Staging")

  def getThread(vm: VirtualMachine, name: String): ThreadReference =
    vm.allThreads.find(_.name == name).getOrElse(null)

  def trace(code: String) = {
    notSupported find { code.contains(_) } match {
      case Some(w) => println(s"Tracing not supported for programs with $w")
      case None    => realTrace(code)
    }
  }

  def realTrace(code: String) = Utils.runAsync {
    try {
      traceListener.onStart()
      verbose = if (System.getProperty("kojo.trace.verbose") == "true") true else false
      turtles.clear()
      pictures.clear()
      evtReqs = Vector[EventRequest]()
      currEvtVec.clear
      hiddenEventCount = 0
      codeLines = code.lines.toVector

      compile(code)
      val vm = launchVM()
      println("Tracing started...")
      val excludes = Array("java.*", "javax.*", "sun.*", "com.sun.*", "com.apple.*", "edu.umd.cs.piccolo.*")

      val evtQueue = vm.eventQueue

      tracingGUI.reset

      breakable {
        while (true) {
          val evtSet = evtQueue.remove()
          for (evt <- evtSet.eventIterator) {
            evt match {

              case threadStartEvt: ThreadStartEvent =>
                val name = threadStartEvt.thread.name
                if (name.contains("Thread-")) {
                  createMethodRequests(excludes, vm, threadStartEvt.thread)
                }

              case methodEnterEvt: MethodEntryEvent =>
                if (!(ignoreMethods.contains(methodEnterEvt.method.name) || methodEnterEvt.method.name.startsWith("apply"))) {
                  currThread = methodEnterEvt.thread
                  try {
                    handleMethodEntry(methodEnterEvt)
                  }
                  catch {
                    case t: Throwable =>
                      println(s"[Exception] [Method Enter] ${methodEnterEvt.method.name} -- ${t.getMessage}")
                  }
                }

              case methodExitEvt: MethodExitEvent =>
                if (!(ignoreMethods.contains(methodExitEvt.method.name) || methodExitEvt.method.name.startsWith("apply"))) {
                  currThread = methodExitEvt.thread
                  try {
                    handleMethodExit(methodExitEvt)
                  }
                  catch {
                    case t: Throwable =>
                      println(s"[Exception] [Method Exit] ${methodExitEvt.method.name} -- ${t.getMessage}")
                  }
                }

              case classPrepare: ClassPrepareEvent =>
                if (classPrepare.referenceType.name == "Wrapper$") {
                  classPrepare.request.disable()
                  createBreakpointRequest(classPrepare.referenceType, vm, currThread)
                }

              case bkpt: BreakpointEvent =>
                bkpt.request.disable()
                createMethodRequests(excludes, vm, currThread)
                createExceptionRequest(excludes, vm)
              //                watchThreadStarts()

              case ee: ExceptionEvent =>
                println(s"Exception in Target: ${ee.exception} at ${ee.location}")

              case vmDcEvt: VMDisconnectEvent =>
                vmRunning = false
                stop()
                println("#"); break

              case vmStartEvt: VMStartEvent =>
                vmRunning = true
                print(">")
                currThread = vmStartEvt.thread
                createClassPrepareRequest(excludes, vm)

              case vmDeathEvt: VMDeathEvent =>
                vmRunning = false
                stop()
                print("<")

              case ue @ _ =>
                println(s"Unhandled trace event: $ue")
            }
          }
          evtSet.resume()
        }
      }
    }
    catch {
      case t: Throwable =>
        System.err.println(s"[Problem] -- ${t.getMessage}")
        vmRunning = false
        stop()
    }
  }

  def printFrameVarInfo(stkfrm: StackFrame) {
    try {
      println(s"Visible Vars: ${stkfrm.visibleVariables}")
      println(s"Argument Values: ${stkfrm.getArgumentValues}")
    }
    catch {
      case t: Throwable =>
    }
  }

  def getCurrentMethodEvent: Option[MethodEvent] = currEvtVec.get(currThread.name)

  def updateCurrentMethodEvent(oNewEvt: Option[MethodEvent]) = oNewEvt match {
    case Some(newEvt) =>
      currEvtVec(currThread.name) = newEvt
    case None =>
      currEvtVec.remove(currThread.name)
  }

  def handleHiddenEvent(desc: String) {
    if (verbose) {
      println(desc)
    }
    else {
      hiddenEventCount += 1
      if (hiddenEventCount % 30 == 0) {
        print(".")
        if (hiddenEventCount % (30 * 30) == 0) {
          print("\n")
        }
      }
    }
  }

  def handleVerboseUiEvent(me: MethodEvent, enter: Boolean) {
    if (verbose) {
      val prefix = if (enter) "[UI Method Enter]" else "[UI Method Exit]"
      print(s"$prefix ${me.methodName}${me.pargs}")
      if (enter) {
        println("")
      }
      else {
        println(s": ${me.pret}")
      }
    }
  }

  def targetToString(frameVal: Value): String = {
    if (frameVal == null) {
      return "null"
    }

    if (frameVal.isInstanceOf[ObjectReference] &&
      !frameVal.isInstanceOf[StringReference] &&
      !frameVal.isInstanceOf[ArrayReference]) {
      val objRef = frameVal.asInstanceOf[ObjectReference]
      val mthd = objRef.referenceType.methodsByName("toString").find(_.argumentTypes.size == 0).get

      evtReqs.foreach(_.disable)
      try {
        //        println(s"Invoking toString for $frameVal")
        val rtrndValue = objRef.invokeMethod(currThread, mthd, new java.util.ArrayList, 0)
        //        println(s"toString done: $rtrndValue")
        rtrndValue.asInstanceOf[StringReference].value()
      }
      catch {
        case inv: InvocationException =>
          println(s"error0 in invokeMethod: ${inv.exception}")
          frameVal.toString
        case inv: Throwable =>
          println(s"error in invokeMethod for $mthd: ${inv.getMessage}")
          frameVal.toString
      }
      finally {
        evtReqs.foreach(_.enable)
      }
    }
    else {
      frameVal.toString
    }
  }

  def localToString(frameVal: Value) = String.valueOf(frameVal)

  def desugar(name0: String) = {
    val name = name0.replaceAllLiterally("$minus$greater", "->").replaceAllLiterally("$times", "*").replaceAllLiterally("_$eq", "")
    val dindex = name.indexOf('$')
    if (dindex == -1) {
      name
    }
    else {
      val ret = name.substring(0, dindex)
      if (ret.length == 0) name else ret
    }
  }

  def handleMethodEntry(methodEnterEvt: MethodEntryEvent) {

    def methodArgs(value: Value => String): Seq[String] = try {
      if (methodEnterEvt.method.arguments.size > 0) {
        methodEnterEvt.method.arguments.map { n =>
          val frame = methodEnterEvt.thread.frame(0)
          val frameVal = frame.getValue(n)
          s"${n.name} = ${value(frameVal)}"
        }
      }
      else {
        Seq()
      }
    }
    catch {
      case e: AbsentInformationException => Seq("AbsentInformationException")
    }

    val methodName = desugar(methodEnterEvt.method.name)
    val srcName = try { methodEnterEvt.location.sourceName } catch { case e: Throwable => "N/A" }
    val callerSrcName = try { currThread.frame(1).location.sourceName } catch { case _: Throwable => "N/A" }
    val lOffset = if (srcName == "scripteditor") lineNumOffset else 0
    val clOffset = if (callerSrcName == "scripteditor") lineNumOffset else 0
    val lineNum = methodEnterEvt.location.lineNumber - lOffset
    val callerLineNum = try { currThread.frame(1).location.lineNumber - clOffset } catch { case _: Throwable => -1 }
    val callerLine = if (callerSrcName == "scripteditor")
      try { codeLines(callerLineNum - 1) } catch { case _: Throwable => "N/A" }
    else
      ""
    val srcLine = if (srcName == "scripteditor")
      try { codeLines(lineNum - 1) } catch { case _: Throwable => "N/A" }
    else
      ""
    val localArgs = try { methodEnterEvt.method.arguments.toList } catch { case e: AbsentInformationException => List[LocalVariable]() }
    val stkfrm = currThread.frame(0)
    def freshStkfrm = currThread.frame(0)

    val methodObject = stkfrm.thisObject
    val methodObjectType = if (methodObject != null) methodObject.referenceType.name else ""

    val isCommand = methodEnterEvt.method.returnTypeName == "void"
    def isTurtleCommand = isCommand && methodObjectType.contains("TracingTurtle")
    def isCanvasCommand = isCommand && methodObjectType.contains("TracingTSCanvas")
    def isBuiltinsCommand = isCommand && methodObjectType.endsWith("TracingBuiltins$")
    def isPicturePackageMethod = methodObjectType contains "net.kogics.kojo.picture."
    def isPictureMethod = methodObjectType == "net.kogics.kojo.picture.Pic"
    def isPictureDraw = methodName == "draw" && isPicturePackageMethod && methodEnterEvt.method.signature == "()V"

    val newEvt = new MethodEvent()
    val mthdEvent = getCurrentMethodEvent
    newEvt.entryLineNum = lineNum
    newEvt.setParent(mthdEvent)
    newEvt.sourceName = srcName
    newEvt.callerSourceName = callerSrcName
    newEvt.srcLine = srcLine
    newEvt.callerLine = callerLine
    newEvt.callerLineNum = callerLineNum
    newEvt.methodName = methodName
    newEvt.targetType = methodObjectType
    newEvt.returnType = methodEnterEvt.method.returnTypeName

    var ret: Option[(Point2D.Double, Point2D.Double)] = None
    if (isTurtleCommand) {
      ret = runTurtleCommand(methodName, stkfrm, localArgs)
    }
    else if (isCanvasCommand) {
      runCanvasCommand(methodName, freshStkfrm, localArgs)
    }
    else if (isBuiltinsCommand) {
      runBuiltinsCommand(methodName, stkfrm, localArgs)
    }
    else if (isPictureDraw) {
      val caller = methodObject.uniqueID
      currPicture = Some(pictures(caller))
    }
//    else if (isPicturePackageMethod) {
//      runPictureMethod(methodName, methodEnterEvt.method.signature, methodObject, methodObjectType, stkfrm, localArgs)
//    }
    newEvt.turtlePoints = ret
    newEvt.picture = currPicture

    if ((srcName == "scripteditor" && lineNum > 0 && srcLine.contains(methodName)) ||
      (callerSrcName == "scripteditor" && callerLine.contains(methodName)) ||
      isPictureDraw) {
      newEvt.args = methodArgs(targetToString)
      newEvt.targetObject = targetToString(methodObject)
      tracingGUI.addStartEvent(newEvt)
      handleVerboseUiEvent(newEvt, true)
    }
    else {
      val desc = s"[Method Enter] ${methodName}${methodEnterEvt.method.signature} in ${methodEnterEvt.method.declaringType.name}"
      //      newEvt.args = methodArgs(localToString)
      handleHiddenEvent(desc)
    }

    updateCurrentMethodEvent(Some(newEvt))
  }

  def handleMethodExit(methodExitEvt: MethodExitEvent) {
    val methodName = desugar(methodExitEvt.method.name)
    val stkfrm = currThread.frame(0)
    val localArgs = try { methodExitEvt.method.arguments.toList } catch { case e: AbsentInformationException => List[LocalVariable]() }
    val retVal = methodExitEvt.returnValue

    handleMethodReturn(methodName, methodExitEvt.method.declaringType.name, methodExitEvt.method.signature, stkfrm, localArgs, retVal)

    val mthdEvent = getCurrentMethodEvent
    mthdEvent.foreach { ce =>
      val lOffset = if (ce.sourceName == "scripteditor") lineNumOffset else 0
      val lineNum = methodExitEvt.location.lineNumber - lOffset
      val retValStr = localToString(retVal)
      ce.exitLineNum = lineNum

      if ((ce.sourceName == "scripteditor" && lineNum > 0 && ce.srcLine.contains(methodName)) ||
        (ce.callerSourceName == "scripteditor" && ce.callerLine.contains(methodName) && ce.returnType != "void")) {
        ce.returnVal = targetToString(retVal)
        tracingGUI.addEndEvent(ce)
        handleVerboseUiEvent(ce, false)
      }
      else {
        ce.returnVal = retValStr
        val desc = s"[Method Exit] ${methodName}${methodExitEvt.method.signature} in ${methodExitEvt.method.declaringType.name}"
        handleHiddenEvent(desc)
      }
      updateCurrentMethodEvent(ce.parent)
    }
  }

  var currPicture: Option[Picture] = None

  def runPictureMethod(name: String, signature: String, methodObject: ObjectReference, methodObjectType: String, stkfrm: StackFrame, localArgs: List[LocalVariable]) {
    if (currPicture.isDefined) {
      return
    }
    println(s"RunPictureMethod: $methodObjectType::${name}${signature}")

    val caller = methodObject.uniqueID
    name match {
      case "translate" if signature.endsWith("CorePicOps;") =>
        val tx = stkfrm.getValue(localArgs(0)).toString.toDouble
        val ty = stkfrm.getValue(localArgs(1)).toString.toDouble
        pictures(caller).translate(tx, ty)
      case "rotate" if signature.endsWith("CorePicOps;") =>
        val angle = stkfrm.getValue(localArgs(0)).toString.toDouble
        pictures(caller).rotate(angle)
      case "scale" if signature.endsWith("CorePicOps;") =>
        val fx = stkfrm.getValue(localArgs(0)).toString.toDouble
        val fy = if (localArgs.length == 2)
          stkfrm.getValue(localArgs(1)).toString.toDouble
        else fx
        pictures(caller).scale(fx, fy)
      case m @ _ =>
      //        println(s"**TODO** - Unimplemented Picture method - $m")
    }
  }

  def runBuiltinsCommand(name: String, stkfrm: StackFrame, localArgs: List[LocalVariable]) {
    import builtins.TSCanvas
    name match {
      case "setBackground" =>
        val c = getColor(stkfrm, stkfrm.getValue(localArgs(0)))
        TSCanvas.tCanvas.setCanvasBackground(c)
      case c @ _ =>
      //        println(s"**TODO** - Unimplemented Builtins command - $c")
    }
  }

  def runCanvasCommand(name: String, stkfrm: => StackFrame, localArgs: List[LocalVariable]) {
    import builtins.TSCanvas
    name match {
      case "clear" =>
        TSCanvas.clear()
      case "cleari" =>
        TSCanvas.cleari()
      case "axesOn" =>
        TSCanvas.axesOn
      case "axesOff" =>
        TSCanvas.axesOff
      case "gridOn" =>
        TSCanvas.gridOn
      case "gridOff" =>
        TSCanvas.gridOff
      case "zoom" =>
        val (x, y, z) = (stkfrm.getValue(localArgs(0)).toString.toDouble, stkfrm.getValue(localArgs(1)).toString.toDouble, stkfrm.getValue(localArgs(0)).toString.toDouble)
        TSCanvas.zoom(x, y, z)
      case "setBackgroundH" =>
        val c1 = getColor(stkfrm, stkfrm.getValue(localArgs(0)))
        val c2 = getColor(stkfrm, stkfrm.getValue(localArgs(1)))
        TSCanvas.setBackgroundH(c1, c2)
      case "setBackgroundV" =>
        val c1 = getColor(stkfrm, stkfrm.getValue(localArgs(0)))
        val c2 = getColor(stkfrm, stkfrm.getValue(localArgs(1)))
        TSCanvas.setBackgroundV(c1, c2)
      case c @ _ =>
      //        println(s"**TODO** - Unimplemented Canvas command - $c")
    }

  }

  def runTurtleCommand(name: String, stkfrm: StackFrame, localArgs: List[LocalVariable]): Option[(Point2D.Double, Point2D.Double)] = {

    import builtins.Tw
    var ret: Option[(Point2D.Double, Point2D.Double)] = None

    val caller = stkfrm.thisObject.uniqueID
    val turtle = if (currPicture.isDefined)
      currPicture.get.asInstanceOf[Pic].t
    else
      turtles.getOrElse(caller, Tw.getTurtle)
    val createdTurtle = turtles.contains(caller)

    name match {
      case "clear" =>
        turtle.clear()
      case "invisible" =>
        turtle.invisible
      case "forward" =>
        if (localArgs.length == 1) {
          val step = stkfrm.getValue(localArgs(0)).toString.toDouble
          turtle.forward(step)
          ret = turtle.lastLine
        }
      case "turn" =>
        val angle = stkfrm.getValue(localArgs(0)).toString.toDouble
        turtle.turn(angle)
      case "home" =>
        turtle.home
      case "jumpTo" =>
        val (x, y) = (stkfrm.getValue(localArgs(0)).toString.toDouble, stkfrm.getValue(localArgs(1)).toString.toDouble)
        turtle.jumpTo(x, y)
      case "setCostume" =>
        val str = stkfrm.getValue(localArgs(0)).toString
        turtle.setCostume(str.substring(1, str.length - 1))
      case "nextCostume" =>
        turtle.nextCostume
      case "setCostumes" =>
        var arg0 = stkfrm.getValue(localArgs(0)).asInstanceOf[ObjectReference]
        if (arg0.toString.contains("Vector")) {
          var costumes = Vector[String]()
          val head = arg0.referenceType.methodsByName("head")(0)
          val tail = arg0.referenceType.methodsByName("tail")(0)
          var arg = arg0
          var done = false

          while (!done) {
            evtReqs.foreach(_.disable)
            try {
              val headValue = arg.invokeMethod(currThread, head, new java.util.ArrayList, ObjectReference.INVOKE_SINGLE_THREADED)
              val tailValue = arg.invokeMethod(currThread, tail, new java.util.ArrayList, ObjectReference.INVOKE_SINGLE_THREADED)
              val str = headValue.asInstanceOf[StringReference].toString()
              arg = tailValue.asInstanceOf[ObjectReference]
              costumes = costumes :+ str.substring(1, str.length - 1)
            }
            catch {
              case inv: InvocationException =>
                done = true
                turtle.setCostumes(costumes)
            }
            evtReqs.foreach(_.enable)
          }
        }
      case "setPosition" =>
        val (x, y) = (stkfrm.getValue(localArgs(0)).toString.toDouble, stkfrm.getValue(localArgs(1)).toString.toDouble)
        turtle.setPosition(x, y)
      case "setPenColor" =>
        val color = getColor(stkfrm, stkfrm.getValue(localArgs(0)))
        turtle.setPenColor(color)
      case "setFillColor" =>
        val color = getColor(stkfrm, stkfrm.getValue(localArgs(0)))
        turtle.setFillColor(color)
      case "setAnimationDelay" =>
        val step = stkfrm.getValue(localArgs(0)).toString.toLong
        turtle.setAnimationDelay(step)
      case "setPenThickness" =>
        val thickness = stkfrm.getValue(localArgs(0)).toString.toDouble
        turtle.setPenThickness(thickness)
      case "penUp" =>
        turtle.penUp
      case "penDown" =>
        turtle.penDown
      case "savePosHe" =>
        turtle.savePosHe
      case "restorePosHe" =>
        turtle.restorePosHe
      case "newTurtle" =>
      // handled on the exit event
      case "changePosition" =>
        val (x, y) = (stkfrm.getValue(localArgs(0)).toString.toDouble, stkfrm.getValue(localArgs(1)).toString.toDouble)
        turtle.changePosition(x, y)
      case "scaleCostume" =>
        val a = stkfrm.getValue(localArgs(0)).toString.toDouble
        turtle.scaleCostume(a)
      case c @ _ =>
      //        println(s"**TODO** - Unimplemented Turtle command - $c")
    }
    ret
  }

  def targetList(list: ObjectReference): List[ObjectReference] = {
    val listType = list.referenceType
    val hdf = listType.fieldByName("scala$collection$immutable$$colon$colon$$hd")
    val tlf = listType.fieldByName("tl")
    val lpics = new ArrayBuffer[ObjectReference]
    var hd = list.getValue(hdf).asInstanceOf[ObjectReference]
    lpics += hd
    var tl = list.getValue(tlf).asInstanceOf[ObjectReference]
    while (tl.referenceType.name != "scala.collection.immutable.Nil$") {
      hd = tl.getValue(hdf).asInstanceOf[ObjectReference]
      lpics += hd
      tl = tl.getValue(tlf).asInstanceOf[ObjectReference]
    }
    lpics.toList
  }

  def handleMethodReturn(name: String, declaringType: String, signature: String, stkfrm: StackFrame, localArgs: List[LocalVariable], retVal: Value) {
    name match {
      case "newTurtle" =>
        import builtins.TSCanvas
        if (localArgs.length == 3) {
          val (x, y, str) = (stkfrm.getValue(localArgs(0)).toString.toDouble, stkfrm.getValue(localArgs(1)).toString.toDouble, stkfrm.getValue(localArgs(2)).toString)
          val newTurtle = TSCanvas.newTurtle(x, y, str.substring(1, str.length - 1))
          val ref = retVal.asInstanceOf[ObjectReference].uniqueID
          turtles(ref) = newTurtle
        }

      case "<init>" =>
        import builtins.TSCanvas
        val caller = stkfrm.thisObject.uniqueID
        declaringType match {
          case "net.kogics.kojo.picture.Pic" =>
            val newPic = picture.Pic { t => }(TSCanvas.tCanvas)
            pictures(caller) = newPic
          case "net.kogics.kojo.picture.Scale" =>
            if (localArgs.length == 2) {
              val factor = stkfrm.getValue(localArgs(0)).toString.toDouble
              val pic = stkfrm.getValue(localArgs(1)).asInstanceOf[ObjectReference].uniqueID
              val newPic = picture.Scale(factor)(pictures(pic))
              pictures(caller) = newPic
            }
          case "net.kogics.kojo.picture.Rot" =>
            if (localArgs.length == 2) {
              val angle = stkfrm.getValue(localArgs(0)).toString.toDouble
              val pic = stkfrm.getValue(localArgs(1)).asInstanceOf[ObjectReference].uniqueID
              val newPic = picture.Rot(angle)(pictures(pic))
              pictures(caller) = newPic
            }
          case "net.kogics.kojo.picture.Trans" =>
            if (localArgs.length == 3) {
              val x = stkfrm.getValue(localArgs(0)).toString.toDouble
              val y = stkfrm.getValue(localArgs(1)).toString.toDouble
              val pic = stkfrm.getValue(localArgs(2)).asInstanceOf[ObjectReference].uniqueID
              val newPic = picture.Trans(x, y)(pictures(pic))
              pictures(caller) = newPic
            }
          case "net.kogics.kojo.picture.GPics" =>
            val pics = stkfrm.getValue(localArgs(0)).asInstanceOf[ObjectReference]
            val picVals = targetList(pics)
            val newPic = picture.GPics(picVals.map { pr => pictures(pr.uniqueID) })
            pictures(caller) = newPic

          case "net.kogics.kojo.picture.HPics" =>
            val pics = stkfrm.getValue(localArgs(0)).asInstanceOf[ObjectReference]
            val picVals = targetList(pics)
            val newPic = picture.HPics(picVals.map { pr => pictures(pr.uniqueID) })
            pictures(caller) = newPic

          case "net.kogics.kojo.picture.VPics" =>
            val pics = stkfrm.getValue(localArgs(0)).asInstanceOf[ObjectReference]
            val picVals = targetList(pics)
            val newPic = picture.HPics(picVals.map { pr => pictures(pr.uniqueID) })
            pictures(caller) = newPic

          case _ =>
        }

      case "draw" if signature == "()V" =>
        val mthdEvent = getCurrentMethodEvent
        var parentPicture = false
        if (mthdEvent.isDefined) {
          if (mthdEvent.get.parent.isDefined) {
            if (mthdEvent.get.parent.get.picture.isDefined) {
              currPicture = mthdEvent.get.parent.get.picture
              parentPicture = true
            }
          }
        }

        if (!parentPicture && currPicture.isDefined) {
          currPicture.get.draw()
          currPicture = None
        }

      case _ =>
    }
  }

  def getColor(stkfrm: StackFrame, arg: Value): Color = {
    val colorVal = arg.asInstanceOf[ObjectReference]
    val str = targetToString(colorVal)
    val pattern = new Regex("\\d{1,3}")
    var rgb = Vector[Int]()
    (pattern findAllIn str).foreach(c => rgb = rgb :+ c.toInt)

    evtReqs.foreach(_.disable)
    val alphaMthd = colorVal.referenceType.methodsByName("getAlpha")(0)
    val alphaValue = colorVal.invokeMethod(currThread, alphaMthd, new java.util.ArrayList, ObjectReference.INVOKE_SINGLE_THREADED)
    val alpha = alphaValue.asInstanceOf[IntegerValue].value
    evtReqs.foreach(_.enable)

    new Color(rgb(0), rgb(1), rgb(2), alpha)
  }

  def watchThreadStarts() {
    val evtReqMgr = currThread.virtualMachine.eventRequestManager

    val thrdStartVal = evtReqMgr.createThreadStartRequest
    thrdStartVal.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    //thrdStartVal.addThreadFilter(mainThread)
    thrdStartVal.enable()
    evtReqs = evtReqs :+ thrdStartVal
  }

  def createMethodRequests(excludes: Array[String], vm: VirtualMachine, thread: ThreadReference) {
    val evtReqMgr = vm.eventRequestManager

    val mthdEnterVal = evtReqMgr.createMethodEntryRequest
    excludes.foreach { mthdEnterVal.addClassExclusionFilter(_) }
    mthdEnterVal.addThreadFilter(thread)
    mthdEnterVal.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    mthdEnterVal.enable()
    evtReqs = evtReqs :+ mthdEnterVal

    val mthdExitVal = evtReqMgr.createMethodExitRequest
    excludes.foreach { mthdExitVal.addClassExclusionFilter(_) }
    mthdExitVal.addThreadFilter(thread)
    mthdExitVal.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    mthdExitVal.enable()
    evtReqs = evtReqs :+ mthdExitVal
  }

  def createExceptionRequest(excludes: Array[String], vm: VirtualMachine) {
    val evtReqMgr = vm.eventRequestManager
    val exceptionRequest = evtReqMgr.createExceptionRequest(null, true, true)
    excludes.foreach { exceptionRequest.addClassExclusionFilter(_) }
    exceptionRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    exceptionRequest.enable()
    evtReqs = evtReqs :+ exceptionRequest
  }

  def createClassPrepareRequest(excludes: Array[String], vm: VirtualMachine) {
    val evtReqMgr = vm.eventRequestManager
    val request = evtReqMgr.createClassPrepareRequest
    excludes.foreach { request.addClassExclusionFilter(_) }
    request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    request.enable()
  }

  def createBreakpointRequest(wrapperType: ReferenceType, vm: VirtualMachine, thread: ThreadReference) {
    val evtReqMgr = vm.eventRequestManager
    val realMain = wrapperType.methodsByName("_main")(0)
    val request = evtReqMgr.createBreakpointRequest(realMain.location)
    request.addThreadFilter(thread)
    request.setSuspendPolicy(EventRequest.SUSPEND_ALL)
    request.enable()
  }
}