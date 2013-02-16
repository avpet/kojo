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
package net.kogics.kojo
package lite

import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Event
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.io.Writer
import java.util.concurrent.CountDownLatch
import java.util.logging.Logger
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JToolBar
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.Utilities
import net.kogics.kojo.core.CodingMode
import net.kogics.kojo.core.D3Mode
import net.kogics.kojo.core.Interpreter
import net.kogics.kojo.core.MwMode
import net.kogics.kojo.core.RunContext
import net.kogics.kojo.core.StagingMode
import net.kogics.kojo.core.TwMode
import net.kogics.kojo.history.CommandHistory
import net.kogics.kojo.lite.canvas.SpriteCanvas
import net.kogics.kojo.livecoding.InteractiveManipulator
import net.kogics.kojo.livecoding.ManipulationContext
import net.kogics.kojo.mathworld.MathWorld
import net.kogics.kojo.turtle.TurtleWorldAPI
import net.kogics.kojo.util.FutureResult
import net.kogics.kojo.util.RichFile.enrichFile
import net.kogics.kojo.xscala.Builtins
import util.Utils

class CodeExecutionSupport(
  TSCanvas: DrawingCanvasAPI,
  Tw: TurtleWorldAPI,
  Staging: staging.API,
  Mw: MathWorld,
  D3: d3.API,
  storyTeller: story.StoryTeller,
  mp3player: music.KMp3,
  fuguePlayer: music.FuguePlayer,
  tCanvas: SpriteCanvas,
  scriptEditor0: => ScriptEditor,
  val kojoCtx: KojoCtx) extends core.CodeExecutionSupport with core.CodeCompletionSupport with ManipulationContext {
  lazy val scriptEditor = scriptEditor0
  import scriptEditor._
  var clearButton = new JButton

  val Log = Logger.getLogger(getClass.getName);
  val promptColor = new Color(178, 66, 0)
  val codeColor = new Color(0x009b00)
  val WorksheetMarker = " //> "

  System.setOut(new PrintStream(new WriterOutputStream(new OutputWindowWriter)))
  val outputPane = new OutputPane(this)
  doWelcome()

  val commandHistory = CommandHistory()
  val historyManager = new HistoryManager

  tCanvas.outputFn = showOutput _
  storyTeller.outputFn = showOutput _

  @volatile var pendingCommands = false
  @volatile var codePane: JTextArea = _
  @volatile var startingUp = true

  val codeRunner = makeCodeRunner()
  val builtins = new Builtins(
    TSCanvas,
    Tw,
    Staging,
    Mw,
    D3,
    storyTeller,
    mp3player,
    fuguePlayer,
    kojoCtx,
    codeRunner
  )

  @volatile var showCode = false
  @volatile var verboseOutput = false
  setActivityListener()

  class OutputWindowWriter extends Writer {
    override def write(s: String) {
      showOutput(s)
    }

    def write(cbuf: Array[Char], off: Int, len: Int) {
      write(new String(cbuf, off, len))
    }

    def close() {}
    def flush() {}
  }

  class WriterOutputStream(writer: Writer) extends OutputStream {

    private val buf = new Array[Byte](1)

    override def close() {
      writer.close()
    }

    override def flush() {
      writer.flush()
    }

    override def write(b: Array[Byte]) {
      writer.write(new String(b))
    }

    override def write(b: Array[Byte], off: Int, len: Int) {
      writer.write(new String(b, off, len))
    }

    def write(b: Int) {
      synchronized {
        buf(0) = b.toByte
        write(buf)
      }
    }
  }

  def setCodePane(cp: JTextArea) {
    codePane = cp;
    codePane.getDocument.addDocumentListener(new DocumentListener {
      def insertUpdate(e: DocumentEvent) {
        clearSButton.setEnabled(true)
        if (hasOpenFile) {
          kojoCtx.fileModified()
        }
      }
      def removeUpdate(e: DocumentEvent) {
        if (codePane.getDocument.getLength == 0) {
          clearSButton.setEnabled(false)
        }
        if (hasOpenFile) {
          kojoCtx.fileModified()
        }
      }
      def changedUpdate(e: DocumentEvent) {}
    })
  }

  def enableRunButton(enable: Boolean) {
    runButton.setEnabled(enable)
    runWorksheetButton.setEnabled(enable)
    compileButton.setEnabled(enable)
  }

  def initPhase2(se: ScriptEditor) {
    clearButton = se.clearButton
    setCodePane(se.codePane)
    hPrevButton.setEnabled(commandHistory.hasPrevious)
  }

  def doWelcome() = {
    val msg = """Welcome to Kojo 2.0!
    |* To use code completion and see online help ->  Press Ctrl+Space or Ctrl+Alt+Space within the Script Editor
    |* To interactively manipulate program output ->  Click on numbers and colors within the Script Editor
    |* To access the context actions for a window ->  Right-Click on the window to bring up its context menu
    |* To Pan or Zoom the Drawing Canvas          ->  Drag the left mouse button or Roll the mouse wheel
    |  * To reset Pan and Zoom levels             ->  Use the Drawing Canvas context menu
    |""".stripMargin

    showOutput(msg)
  }

  def makeCodeRunner(): core.CodeRunner = {
    new core.ProxyCodeRunner(makeRealCodeRunner _)
  }

  def isSingleLine(code: String): Boolean = {
    //    val n = code.count {c => c == '\n'}
    //    if (n > 1) false else true

    val len = code.length
    var idx = 0
    var count = 0
    while (idx < len) {
      if (code.charAt(idx) == '\n') {
        count += 1
        if (count > 1) {
          return false
        }
      }
      idx += 1
    }
    if (count == 0) {
      return true
    }
    else {
      if (code.charAt(len - 1) == '\n') {
        return true
      }
      else {
        return false
      }
    }
  }

  def makeRealCodeRunner: core.CodeRunner = {
    val codeRunner = new xscala.ScalaCodeRunner(new RunContext {

      @volatile var suppressInterpOutput = false
      @volatile var astStopPhase = "typer"

      def initInterp(interp: Interpreter) {
        interp.bind("predef", "net.kogics.kojo.lite.CodeExecutionSupport", CodeExecutionSupport.this)
        interp.interpret("val builtins = predef.builtins")
        interp.interpret("import builtins._")
      }

      def onInterpreterInit() = {
        showOutput(" " * 38 + "_____\n\n")
        outputPane.clearLastOutput()
        startingUp = false
      }

      def onInterpreterStart(code: String) {
        resetErrInfo()
        if (verboseOutput || isSingleLine(code)) {
          suppressInterpOutput = false
        }
        else {
          suppressInterpOutput = true
        }

        showNormalCursor()
        enableRunButton(false)
        stopButton.setEnabled(true)
      }

      def onCompileStart() {
        resetErrInfo()
        showNormalCursor()
        enableRunButton(false)
      }

      def onRunError() {
        interpreterDone()
        onError()
      }

      def onCompileError() {
        compileDone()
        onError()
      }

      private def onError() {
        Utils.runInSwingThread {
          statusStrip.onError()
        }
        // just in case this was a story
        // bad coupling here!
        storyTeller.storyAborted()
      }

      def onCompileSuccess() {
        compileDone()
        Utils.runInSwingThread {
          statusStrip.onSuccess()
        }
      }

      private def compileDone() {
        codePane.requestFocusInWindow
        enableRunButton(true)
        //        Utils.schedule(0.2) {
        //          kojoCtx.scrollOutputToEnd()
        //        }
      }

      def onRunSuccess() = {
        interpreterDone()
        Utils.runInSwingThread {
          statusStrip.onSuccess()
        }
        Utils.schedule(0.2) {
          kojoCtx.scrollOutputToEnd()
        }
      }

      def onRunInterpError() = {
        showErrorMsg("Kojo is unable to process your script. Please modify your code and try again.\n")
        showOutput("More information about the problem - can be viewed - by clicking the red icon at the bottom right of the Kojo screen.\n")
        onRunError()
      }

      def onInternalCompilerError() = {
        showErrorMsg("Kojo is unable to process your script. Please modify your code and try again.\n")
        showOutput("More information about the problem - can be viewed - by clicking the red icon at the bottom right of the Kojo screen.\n")
        onCompileError()
      }

      def kprintln(outText: String) {
        showOutput(outText)
      }

      def reportOutput(outText: String) {
        if (suppressInterpOutput) {
          return
        }

        kprintln(outText)
      }

      def reportErrorMsg(errMsg: String) {
        showErrorMsg(errMsg)
      }

      def reportErrorText(errText: String) {
        showErrorText(errText)
      }

      def reportSmartErrorText(errText: String, line: Int, column: Int, offset: Int) {
        showSmartErrorText(errText, line, column, offset)
      }

      private def interpreterDone() = {
        Utils.runInSwingThread {
          enableRunButton(true)
          if (!pendingCommands) {
            stopButton.setEnabled(false)
          }
        }
      }

      def showScriptInOutput() { showCode = true }
      def hideScriptInOutput() { showCode = false }
      def showVerboseOutput() { verboseOutput = true }
      def hideVerboseOutput() { verboseOutput = false }
      def readInput(prompt: String): String = CodeExecutionSupport.this.readInput(prompt)

      def clearOutput() = clrOutput()

      def setScript(code: String) {
        Utils.runInSwingThreadAndWait {
          closeFileAndClrEditor()
          codePane.setText(code)
          codePane.setCaretPosition(0)
        }
      }

      def reportWorksheetOutput(result: String, lineNum: Int) {
        appendToCodePaneLine(lineNum, result.replaceAll("\n(.+)", " | $1"))
      }

      private def appendToCodePaneLine(lineNum: Int, result: String) = Utils.runInSwingThread {
        val insertPos = getVisibleLineEndOffset(lineNum + selectionOffset)
        val dot = codePane.getCaretPosition
        val selStart = codePane.getSelectionStart()
        val selEnd = codePane.getSelectionEnd()
        codePane.insert(WorksheetMarker + result.trim, insertPos)
        if (dot == insertPos) {
          if (selStart == selEnd) {
            codePane.setCaretPosition(dot)
          }
          else {
            codePane.setSelectionStart(selStart)
            codePane.setSelectionEnd(selEnd)
          }
        }
      }

      def insertCodeInline(code: String) = smartInsertCode(code, false)
      def insertCodeBlock(code: String) = smartInsertCode(code, true)

      private def smartInsertCode(code: String, block: Boolean) = Utils.runInSwingThread {
        val dot = codePane.getCaretPosition
        val cOffset = code.indexOf("${c}")
        if (cOffset == -1) {
          if (block) {
            val leadingSpaces = dot - Utilities.getRowStart(codePane, dot)
            codePane.insert("%s\n".format(code).
              replaceAllLiterally("\n", "\n%s".format(" " * leadingSpaces)), dot)
            // move to next line. Assumes that a block insert without a ${c} is on a single line - like clear() etc  
            codePane.setCaretPosition(Utilities.getRowEnd(codePane, dot) + 1 + leadingSpaces)
          }
          else {
            codePane.insert("%s ".format(code), dot)
          }
        }
        else {
          if (block) {
            val leadingSpaces = dot - Utilities.getRowStart(codePane, dot)
            codePane.insert("%s\n".format(code.replaceAllLiterally("${c}", "")).
              replaceAllLiterally("\n", "\n%s".format(" " * leadingSpaces)), dot)
            codePane.setCaretPosition(dot + cOffset)
          }
          else {
            codePane.insert("%s ".format(code.replaceAllLiterally("${c}", "")), dot)
            codePane.setCaretPosition(dot + cOffset)
          }
        }
        activateEditor()
      }

      def clickRun() {
        runCode()
      }

      def stopActivity() {
        CodeExecutionSupport.this.stopActivity()
      }

      def setAstStopPhase(phase: String): Unit = astStopPhase = phase
    })
    codeRunner
  }

  def isRunningEnabled = runButton.isEnabled

  def setActivityListener() {
    kojoCtx.setActivityListener(new core.AbstractSpriteListener {
      def interpreterDone = runButton.isEnabled
      override def hasPendingCommands {
        pendingCommands = true
        stopButton.setEnabled(true)
      }
      override def pendingCommandsDone {
        pendingCommands = false
        if (interpreterDone) stopButton.setEnabled(false)
      }
    })
  }

  def upload() {
    val dlg = new codex.CodeExchangeForm(kojoCtx, true)
    dlg.setCanvas(tCanvas)
    dlg.setCode(codePane.getText())
    dlg.centerScreen()
  }

  def clrOutput() {
    Utils.runInSwingThread {
      outputPane.clear()
      clearButton.setEnabled(false)
      codePane.requestFocusInWindow
    }
    outputPane.clearLastOutput()
  }

  def enableClearButton() = if (!clearButton.isEnabled) clearButton.setEnabled(true)

  def readInput(prompt: String): String = {
    val input = new FutureResult[String]
    Utils.runInSwingThread {
      val inputField = outputPane.createReadInputPanel(prompt)
      kojoCtx.activateOutputPane()
      Utils.schedule(0.1) { inputField.requestFocusInWindow() }
      Utils.schedule(0.9) { inputField.requestFocusInWindow() }
      inputField.addActionListener(new ActionListener {
        def actionPerformed(e: ActionEvent) {
          println("%s: %s" format (prompt, inputField.getText))
          input.set(inputField.getText)
          outputPane.removeInputPanel()
        }
      })
    }
    input.get
  }

  def appendOutput(s: String, color: Color) = outputPane.appendOutput(s, color)
  def setOutputBackground(color: Color) = outputPane.setOutputBackground(color)
  def setOutputForeground(color: Color) = outputPane.setOutputForeground(color)
  def setOutputFontSize(size: Int) = outputPane.setOutputFontSize(size)
  def increaseOutputFontSize() = outputPane.increaseOutputFontSize()
  def decreaseOutputFontSize() = outputPane.decreaseOutputFontSize()

  def showOutput(outText: String) = outputPane.showOutput(outText)
  def showOutput(outText: String, color: Color) = outputPane.showOutput(outText, color)
  def resetErrInfo() = outputPane.resetErrInfo()
  def showErrorMsg(errMsg: String) = outputPane.showErrorMsg(errMsg)
  def showErrorText(errText: String) = outputPane.showErrorText(errText)
  def showSmartErrorText(errText: String, line: Int, column: Int, offset: Int) =
    outputPane.showSmartErrorText(errText, line, column, offset)

  def showWaitCursor() {
    val wc = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
    codePane.setCursor(wc)
    tCanvas.setCursor(wc)
  }

  def showNormalCursor() {
    val nc = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR)
    codePane.setCursor(nc);
    tCanvas.setCursor(nc)
  }

  def stopScript() {
    stopInterpreter()
    stopActivity()
  }

  def stopInterpreter() {
    codeRunner.interruptInterpreter()
  }

  def stopActivity() {
    Utils.stopMonitoredThreads()
    tCanvas.stop()
    fuguePlayer.stopMusic()
    fuguePlayer.stopBgMusic()
    mp3player.stopMp3()
    mp3player.stopMp3Loop()
  }

  def invalidCode(code: String): Boolean = {
    if (code == null || code.trim.length == 0) true
    else false
  }

  def parseCode(browseAst: Boolean) = compileParseCode(false, browseAst)
  def compileCode() = compileParseCode(true, false)

  private def compileParseCode(check: Boolean, browseAst: Boolean) {
    val code = codePane.getText()

    if (invalidCode(code)) {
      return
    }

    statusStrip.onDocChange()
    enableRunButton(false)
    showWaitCursor()

    val cleanCode = cleanWsOutput(CodeToRun(code, None))
    if (check) {
      codeRunner.compileCode(cleanCode)
    }
    else {
      codeRunner.parseCode(cleanCode, browseAst)
    }
  }

  def isStory(code: String) = {
    code.indexOf("stPlayStory") != -1
  }

  def isWorksheet(code: String) = {
    code.indexOf("#worksheet") != -1
  }

  def compileRunCode() {
    val code2run = codeToRun
    preProcessCode(code2run.code)
    codeRunner.compileRunCode(code2run.code)
  }

  // For IPM
  def runCode(code: String) = codeRunner.runCode(code) // codeRunner.runCode(cleanWsOutput(code)) 

  case class CodeToRun(code: String, selection: Option[(Int, Int)])

  def codeToRun: CodeToRun = {
    val code = codePane.getText
    val selectedCode = codePane.getSelectedText
    if (selectedCode == null) {
      CodeToRun(code, None)
    }
    else {
      CodeToRun(selectedCode, Some(codePane.getSelectionStart, codePane.getSelectionEnd))
    }
  }

  def runCode() {
    // Runs on swing thread
    val code2run = codeToRun
    if (isWorksheet(code2run.code)) {
      runWorksheet(code2run)
    }
    else {
      val code = cleanWsOutput(code2run)
      preProcessCode(code)
      if (isStory(code)) {
        codeRunner.compileRunCode(code)
      }
      else {
        codeRunner.runCode(code)
      }
    }
  }

  def runWorksheet() {
    runWorksheet(codeToRun)
  }

  // impure function!
  def extendSelection(code2run: CodeToRun) = {
    code2run.selection.map {
      case (selStart, selEnd) =>
        val selStartLine = codePane.getLineOfOffset(selStart)
        val selEndLine = codePane.getLineOfOffset(selEnd)
        val selStartLineStart = codePane.getLineStartOffset(selStartLine)
        val selStartLineEnd = getVisibleLineEndOffset(selStartLine)
        val selEndLineStart = codePane.getLineStartOffset(selEndLine)
        val selEndLineEnd = getVisibleLineEndOffset(selEndLine)
        val newSelStart = if (selStartLineEnd == selStart) selStart else selStartLineStart
        val newSelEnd = if (selEndLineStart == selEnd) selEnd else selEndLineEnd
        codePane.setSelectionStart(newSelStart)
        codePane.setSelectionEnd(newSelEnd)
        CodeToRun(codePane.getSelectedText, Some(newSelStart, newSelEnd))
    } getOrElse code2run
  }

  def runWorksheet(code2run0: CodeToRun) {
    val code2run = extendSelection(code2run0)
    val cleanCode = removeWorksheetOutput(code2run.code)
    setWorksheetScript(cleanCode, code2run.selection)
    preProcessCode(cleanCode)
    codeRunner.runWorksheet(cleanCode)
  }

  // Impure function!
  def cleanWsOutput(code2run: CodeToRun) = {
    val code = code2run.code
    if (code.contains(WorksheetMarker)) {
      val cleanCode = removeWorksheetOutput(code)
      setWorksheetScript(cleanCode, code2run.selection)
      cleanCode
    }
    else {
      code
    }
  }

  def removeWorksheetOutput(code: String) = code.replaceAll(s"${WorksheetMarker}.*", "")

  private def getVisibleLineEndOffset(line: Int) = {
    def newLineBefore(pos: Int) = codePane.getText(pos - 1, 1) == "\n"
    val lineStart = codePane.getLineStartOffset(line)
    val lineEnd = codePane.getLineEndOffset(line)
    if (newLineBefore(lineEnd) && lineStart != lineEnd) lineEnd - 1 else lineEnd
  }

  var selectionOffset = 0

  def setWorksheetScript(code: String, selection: Option[(Int, Int)]) = Utils.runInSwingThread {
    val dot = codePane.getCaretPosition
    val line = codePane.getLineOfOffset(dot)
    val offsetInLine = dot - codePane.getLineStartOffset(line)
    if (selection.isDefined) {
      val selStart = selection.get._1
      selectionOffset = codePane.getLineOfOffset(selStart)
      codePane.replaceRange(code, selStart, selection.get._2)
      codePane.setSelectionStart(selStart)
      codePane.setSelectionEnd(selStart + code.length)
    }
    else {
      selectionOffset = 0
      codePane.setText(code)
      try {
        val lineStart = codePane.getLineStartOffset(line)
        val lineEnd = getVisibleLineEndOffset(line)
        val newLineSize = lineEnd - lineStart
        codePane.setCaretPosition(lineStart + math.min(offsetInLine, newLineSize))
      }
      catch {
        case t: Throwable =>
          println(s"Problem placing Carent: $t.getMessage")
          codePane.setCaretPosition(0)
      }
    }
  }

  def preProcessCode(code: String) {
    // now that we use the proxy code runner, disable the run button right away and change
    // the cursor so that the user gets some feedback the first time he runs something
    // - relevant if the proxy is still loading the real runner
    enableRunButton(false)
    showWaitCursor()

    if (isStory(code)) {
      // a story
      activateTw()
      storyTeller.storyComing()
    }

    if (showCode) {
      showOutput("\n>>>\n", promptColor)
      showOutput(code, codeColor)
      showOutput("\n<<<\n", promptColor)
    }
    else {
      outputPane.maybeOutputDelimiter()
    }
    historyManager.codeRun(code)
  }

  def codeFragment(caretOffset: Int) = {
    val cpt = codePane.getText
    if (caretOffset > cpt.length) ""
    else cpt.substring(0, caretOffset)
  }
  def varCompletions(prefix: Option[String]) = codeRunner.varCompletions(prefix)
  def keywordCompletions(prefix: Option[String]) = codeRunner.keywordCompletions(prefix)
  def memberCompletions(caretOffset: Int, objid: String, prefix: Option[String]) = codeRunner.memberCompletions(codePane.getText, caretOffset, objid, prefix)
  def objidAndPrefix(caretOffset: Int): (Option[String], Option[String]) = xscala.CodeCompletionUtils.findIdentifier(codeFragment(caretOffset))
  def typeAt(caretOffset: Int) = codeRunner.typeAt(codePane.getText, caretOffset)

  var imanip: Option[InteractiveManipulator] = None
  def addManipulator(im: InteractiveManipulator) {
    imanip = Some(im)
  }
  def removeManipulator(im: InteractiveManipulator) {
    imanip = None
  }

  def loadCodeFromHistoryPrev() = historyManager.historyMoveBack
  def loadCodeFromHistoryNext() = historyManager.historyMoveForward
  def loadCodeFromHistory(historyIdx: Int) = historyManager.setCode(historyIdx)

  class HistoryManager {

    def historyMoveBack {
      // depend on history listener mechanism to move back
      val prevCode = commandHistory.previous
      hPrevButton.setEnabled(commandHistory.hasPrevious)
      hNextButton.setEnabled(true)
      commandHistory.ensureLastEntryVisible()
    }

    def historyMoveForward {
      // depend on history listener mechanism to move forward
      val nextCode = commandHistory.next
      if (!nextCode.isDefined) {
        hNextButton.setEnabled(false)
      }
      hPrevButton.setEnabled(true)
      commandHistory.ensureLastEntryVisible()
    }

    def updateButtons(historyIdx: Int) {
      if (commandHistory.size > 0 && historyIdx != 0)
        hPrevButton.setEnabled(true)
      else
        hPrevButton.setEnabled(false)

      if (historyIdx < commandHistory.size)
        hNextButton.setEnabled(true)
      else
        hNextButton.setEnabled(false)
    }

    def setCode(historyIdx: Int) {
      updateButtons(historyIdx)
      val codeAtIdx = commandHistory.toPosition(historyIdx)

      if (codeAtIdx.isDefined) {
        codePane.setText(codeAtIdx.get)
        codePane.setCaretPosition(0)
      }

      //        codePane.requestFocusInWindow
    }

    def codeRunError() = {
    }

    def codeRun(code: String) {
      val tcode = code.trim()
      commandHistory.add(code, openedFile.map(f => "%s (%s)" format (f.getName, f.getParent)))
    }
  }

  def activateEditor() = kojoCtx.activateScriptEditor()

  @volatile var codingMode: CodingMode = TwMode // default mode for interp

  def activateTw() {
    if (codingMode != TwMode) {
      codingMode = TwMode
      codeRunner.activateTw()
    }
  }

  def activateStaging() {
    if (codingMode != StagingMode) {
      codingMode = StagingMode
      codeRunner.activateStaging()
    }
  }

  def activateMw() {
    if (codingMode != MwMode) {
      codingMode = MwMode
      codeRunner.activateMw()
    }
  }

  def activateD3() {
    if (codingMode != D3Mode) {
      codingMode = D3Mode
      codeRunner.activateD3()
    }
  }

  def isTwActive = codingMode == TwMode
  def isStagingActive = codingMode == StagingMode
  def isMwActive = codingMode == MwMode
  def isD3Active = codingMode == D3Mode

  def knownColors = kojoCtx.knownColors
}
