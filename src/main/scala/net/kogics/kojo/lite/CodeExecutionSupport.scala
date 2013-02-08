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

import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Event
import java.awt.Font
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

import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JToolBar
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext
import javax.swing.text.Utilities

import net.kogics.kojo.core.CodingMode
import net.kogics.kojo.core.D3Mode
import net.kogics.kojo.core.InitedSingleton
import net.kogics.kojo.core.MwMode
import net.kogics.kojo.core.RunContext
import net.kogics.kojo.core.StagingMode
import net.kogics.kojo.core.TwMode
import net.kogics.kojo.history.CommandHistory
import net.kogics.kojo.lite.canvas.SpriteCanvas
import net.kogics.kojo.livecoding.InteractiveManipulator
import net.kogics.kojo.livecoding.ManipulationContext
import net.kogics.kojo.util.FutureResult
import net.kogics.kojo.util.RichFile.enrichFile
import net.kogics.kojo.util.TerminalAnsiCodes

import util.Utils

object CodeExecutionSupport extends InitedSingleton[CodeExecutionSupport] {
  def initedInstance(codePane: JTextArea, ctx: KojoCtx) = synchronized {
    instanceInit()
    val ret = instance()
    ret.kojoCtx = ctx
    ret.setCodePane(codePane)
    ret
  }

  protected def newInstance = new CodeExecutionSupport()
}

class CodeExecutionSupport private extends core.CodeCompletionSupport with ManipulationContext {
  val Log = Logger.getLogger(getClass.getName);
  val promptColor = new Color(178, 66, 0)
  val codeColor = new Color(0x009b00)
  val DefaultOutputColor = new Color(32, 32, 32)
  val DefaultOutputFontSize = 13
  val WorksheetMarker = " //> "
  var outputColor = DefaultOutputColor

  val (toolbar, runButton, runWorksheetButton, compileButton, stopButton, hNextButton, hPrevButton,
    clearSButton, clearButton, cexButton) = makeToolbar()
  val outputWindow = new JTextPane
  outputWindow.setForeground(new Color(32, 32, 32))
  outputWindow.setBackground(Color.white)

  val errorWindow = new JEditorPane
  errorWindow.setContentType("text/html")

  val outLayout = new CardLayout
  val outPanel = new JPanel(outLayout)
  val outoutPanel = new JPanel(new BorderLayout)
  val outoutSp = new JScrollPane(outputWindow)
  outoutSp.setBorder(BorderFactory.createEmptyBorder())
  outoutPanel.add(outoutSp, BorderLayout.CENTER)
  var readInputPanel: JPanel = new JPanel
  outPanel.add(outoutPanel, "Output")
  outPanel.add(new JScrollPane(errorWindow), "Error")

  outputWindow.setEditable(false)
  errorWindow.setEditable(false)
  System.setOut(new PrintStream(new WriterOutputStream(new OutputWindowWriter)))
  doWelcome()

  val commandHistory = CommandHistory.instance
  val historyManager = new HistoryManager
  hPrevButton.setEnabled(commandHistory.hasPrevious)

  val tCanvas = SpriteCanvas.instance
  tCanvas.outputFn = showOutput _
  val storyTeller = story.StoryTeller.instance
  storyTeller.outputFn = showOutput _

  val fuguePlayer = music.FuguePlayer.instance
  val mp3player = music.KMp3.instance

  @volatile var pendingCommands = false
  @volatile var runMonitor: RunMonitor = new NoOpRunMonitor()
  @volatile var codePane: JTextArea = _
  @volatile var kojoCtx: KojoCtx = _
  @volatile var startingUp = true

  val codeRunner = makeCodeRunner()

  val statusStrip = new StatusStrip()

  @volatile var showCode = false
  @volatile var verboseOutput = false
  val OutputDelimiter = "---\n"
  @volatile var lastOutput = ""

  setSpriteListener()

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
    addCodePaneHandlers()
    statusStrip.linkToPane()
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

  def makeToolbar() = {
    val RunScript = "RunScript"
    val RunWorksheet = "RunWorksheet"
    val CompileScript = "CompileScript"
    val StopScript = "StopScript"
    val HistoryNext = "HistoryNext"
    val HistoryPrev = "HistoryPrev"
    val ClearEditor = "ClearEditor"
    val ClearOutput = "ClearOutput"
    val UploadCommand = "UploadCommand"

    val actionListener = new ActionListener {
      def actionPerformed(e: ActionEvent) = e.getActionCommand match {
        case RunScript =>
          if ((e.getModifiers & Event.CTRL_MASK) == Event.CTRL_MASK) {
            compileRunCode()
          }
          else {
            runCode()
          }
          codePane.requestFocusInWindow()
        case RunWorksheet =>
          runWorksheet()
          codePane.requestFocusInWindow()
        case CompileScript =>
          if ((e.getModifiers & Event.CTRL_MASK) == Event.CTRL_MASK) {
            parseCode(false)
          }
          else if ((e.getModifiers & Event.SHIFT_MASK) == Event.SHIFT_MASK) {
            parseCode(true)
          }
          else {
            compileCode()
          }
        case StopScript =>
          stopScript()
          codePane.requestFocusInWindow()
        case HistoryNext =>
          loadCodeFromHistoryNext()
          codePane.requestFocusInWindow()
        case HistoryPrev =>
          loadCodeFromHistoryPrev()
          codePane.requestFocusInWindow()
        case ClearEditor =>
          closeFileAndClrEditorIgnoringCancel()
        case ClearOutput =>
          clrOutput()
        case UploadCommand =>
          upload()
      }
    }

    def makeNavigationButton(imageFile: String, actionCommand: String,
                             toolTipText: String, altText: String): JButton = {
      val button = new JButton()
      button.setActionCommand(actionCommand)
      button.setToolTipText(toolTipText)
      button.addActionListener(actionListener)
      button.setIcon(Utils.loadIcon(imageFile, altText))
      // button.setMnemonic(KeyEvent.VK_ENTER)
      button;
    }

    val toolbar = new JToolBar
    toolbar.setPreferredSize(new Dimension(100, 24))

    val runButton = makeNavigationButton("/images/run24.png", RunScript, Utils.loadString("S_RunScript"), "Run the Code")
    val runWorksheetButton = makeNavigationButton("/images/runw24.png", RunWorksheet, Utils.loadString("S_RunWorksheet"), "Run the Code as a Worksheet")
    val compileButton = makeNavigationButton("/images/check.png", CompileScript, Utils.loadString("S_CheckScript"), "Check the Code")
    val stopButton = makeNavigationButton("/images/stop24.png", StopScript, Utils.loadString("S_StopScript"), "Stop the Code")
    val hNextButton = makeNavigationButton("/images/history-next.png", HistoryNext, Utils.loadString("S_HistNext"), "Next in History")
    val hPrevButton = makeNavigationButton("/images/history-prev.png", HistoryPrev, Utils.loadString("S_HistPrev"), "Prev in History")
    val clearSButton = makeNavigationButton("/images/clears.png", ClearEditor, Utils.loadString("S_ClearEditorT"), "Clear the Editor and Close Open File")
    val clearButton = makeNavigationButton("/images/clear24.png", ClearOutput, Utils.loadString("S_ClearOutput"), "Clear the Output")
    val cexButton = makeNavigationButton("/images/upload.png", UploadCommand, Utils.loadString("S_Upload"), "Upload")

    toolbar.add(runButton)
    toolbar.add(runWorksheetButton)

    stopButton.setEnabled(false)
    toolbar.add(stopButton)

    toolbar.add(compileButton)

    toolbar.add(hPrevButton)

    hNextButton.setEnabled(false)
    toolbar.add(hNextButton)

    clearSButton.setEnabled(false)
    toolbar.add(clearSButton)

    toolbar.addSeparator()

    toolbar.add(cexButton)

    clearButton.setEnabled(false)
    toolbar.add(clearButton)

    (toolbar, runButton, runWorksheetButton, compileButton, stopButton, hNextButton, hPrevButton, clearSButton, clearButton, cexButton)
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

      def onInterpreterInit() = {
        showOutput(" " * 38 + "_____\n\n")
        lastOutput = ""
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
        runMonitor.onRunStart()
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
        runMonitor.reportOutput(outText)
      }

      def reportOutput(outText: String) {
        if (suppressInterpOutput) {
          return
        }

        kprintln(outText)
      }

      def reportErrorMsg(errMsg: String) {
        showErrorMsg(errMsg)
        runMonitor.reportOutput(errMsg)
      }

      def reportErrorText(errText: String) {
        showErrorText(errText)
        runMonitor.reportOutput(errText)
      }

      def reportSmartErrorText(errText: String, line: Int, column: Int, offset: Int) {
        showSmartErrorText(errText, line, column, offset)
        runMonitor.reportOutput(errText)
      }

      private def interpreterDone() = {
        Utils.runInSwingThread {
          enableRunButton(true)
          if (!pendingCommands) {
            stopButton.setEnabled(false)
          }
        }
        runMonitor.onRunEnd()
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

      def stopAnimation() {
        CodeExecutionSupport.this.stopAnimation()
      }
    }, tCanvas)
    codeRunner
  }

  def isRunningEnabled = runButton.isEnabled

  def addCodePaneHandlers() {
    codePane.addKeyListener(new KeyAdapter {
      override def keyPressed(evt: KeyEvent) {
        imanip.foreach { _ close () }
        evt.getKeyCode match {
          case KeyEvent.VK_ENTER =>
            if (evt.isControlDown && (isRunningEnabled || evt.isShiftDown)) {
              runCode()
              evt.consume
            }
            else if (evt.isShiftDown && isRunningEnabled) {
              runWorksheet()
              evt.consume
            }
          case KeyEvent.VK_UP =>
            if (evt.isControlDown && hPrevButton.isEnabled) {
              loadCodeFromHistoryPrev()
              evt.consume
            }
          case KeyEvent.VK_DOWN =>
            if (evt.isControlDown && hNextButton.isEnabled) {
              loadCodeFromHistoryNext()
              evt.consume
            }
          case _ => // do nothing special
        }
      }

    })
  }

  def setSpriteListener() {
    tCanvas.setTurtleListener(new core.AbstractSpriteListener {
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
      outputColor = DefaultOutputColor
      outputWindow.setBackground(Color.white)
      setOutputFontSize(DefaultOutputFontSize)
      outputWindow.setText("")
      errorWindow.setText("")
      outoutPanel.remove(readInputPanel)
      outoutPanel.revalidate()
      clearButton.setEnabled(false)
      codePane.requestFocusInWindow
    }
    lastOutput = ""
  }

  def enableClearButton() = if (!clearButton.isEnabled) clearButton.setEnabled(true)

  def readInput(prompt: String): String = {
    val input = new FutureResult[String]
    Utils.runInSwingThread {
      readInputPanel = new JPanel()
      readInputPanel.setLayout(new BoxLayout(readInputPanel, BoxLayout.Y_AXIS))
      val label = new JLabel(" %s" format (prompt))
      label.setAlignmentX(Component.LEFT_ALIGNMENT)
      val inputField = new JTextField
      inputField.setAlignmentX(Component.LEFT_ALIGNMENT)
      readInputPanel.add(label)
      readInputPanel.add(inputField)
      outoutPanel.add(readInputPanel, BorderLayout.SOUTH)
      outoutPanel.revalidate()
      kojoCtx.activateOutputPane()
      Utils.schedule(0.1) { inputField.requestFocusInWindow() }
      Utils.schedule(0.9) { inputField.requestFocusInWindow() }
      inputField.addActionListener(new ActionListener {
        def actionPerformed(e: ActionEvent) {
          println("%s: %s" format (prompt, inputField.getText))
          input.set(inputField.getText)
          outoutPanel.remove(readInputPanel)
          outoutPanel.revalidate()
          //          kojoCtx.activateScriptEditor()
        }
      })
    }
    input.get
  }

  val baseStyle = StyleContext.getDefaultStyleContext.getStyle(StyleContext.DEFAULT_STYLE)
  def appendOutput(s: String, color: Color) {
    if (TerminalAnsiCodes.isColoredString(s)) {
      TerminalAnsiCodes.parse(s) foreach { cstr =>
        appendOutput(cstr._1, cstr._2)
      }
    }
    else {
      val doc = outputWindow.getStyledDocument()
      var colorStyle = doc.getStyle(color.getRGB().toString)
      if (colorStyle == null) {
        colorStyle = doc.addStyle(color.getRGB().toString, baseStyle)
        StyleConstants.setForeground(colorStyle, color)
      }

      doc.insertString(doc.getLength, s, colorStyle)
      outputWindow.setCaretPosition(doc.getLength)
      outLayout.show(outPanel, "Output")
    }
  }

  def setOutputBackground(color: Color) = Utils.runInSwingThread {
    // works after nimbus painter hack
    outputWindow.setBackground(color)

    // problem with code below: 
    // works only for previous text
    // does not fill out the whole background
    //    val background = new SimpleAttributeSet()
    //    StyleConstants.setBackground(background, color)
    //    val doc = outputWindow.getStyledDocument
    //    doc.setCharacterAttributes(0, doc.getLength, background, false)
  }

  def setOutputForeground(color: Color) = Utils.runInSwingThread {
    outputColor = color
  }

  var fontSize = DefaultOutputFontSize
  def setOutputFontSize(size: Int) = Utils.runInSwingThread {
    fontSize = size
    outputWindow.setFont(new Font(Font.MONOSPACED, Font.PLAIN, size))
  }

  def increaseOutputFontSize() {
    setOutputFontSize(fontSize + 1)
  }

  def decreaseOutputFontSize() {
    setOutputFontSize(fontSize - 1)
  }

  setOutputFontSize(fontSize)

  @volatile var errText = ""
  @volatile var errOffset = 0
  @volatile var errCount = 0

  errorWindow.addHyperlinkListener(new HyperlinkListener {
    val linkRegex = """(?i)http://error/(\d+)""".r
    def hyperlinkUpdate(e: HyperlinkEvent) {
      if (e.getEventType == HyperlinkEvent.EventType.ACTIVATED) {
        e.getURL.toString match {
          case linkRegex(offset) =>
            codePane.select(offset.toInt, offset.toInt + 1)
            kojoCtx.activateScriptEditor()
          case _ =>
        }
      }
    }
  })

  def resetErrInfo() {
    errText = ""
    errOffset = 0
    errCount = 0
    Utils.runInSwingThread {
      errorWindow.setText("")
      outLayout.show(outPanel, "Output")
    }
  }

  def appendError(s: String, offset: Option[Int] = None) {
    errText += xml.Unparsed(s)
    if (offset.isDefined) {
      // errCount is used only for 'Check Script' case
      errCount += 1
      if (errCount == 1) {
        errOffset = offset.get
      }
    }

    def errorLink = "http://error/" + errOffset

    def errorLocation =
      <div style="margin:5px;font-size:large;">
      { if (errCount > 1) { <a href={ errorLink }>Locate first error in script</a> } else if (errCount == 1) { <a href={ errorLink }>Locate error in script</a> } else { <span style="color:blue;">Use the 'Check Script' button for better error recovery.</span> } }
      </div>

    val errMsg =
      <body style="">
        <h2>There's a problem in your script!</h2> 
        { errorLocation }
        <div style="color:red;margin:5px;font-size:large;">
          <pre>{ errText }</pre>
        </div>
        { if (errCount > 2) errorLocation }
      </body>

    errorWindow.setText(errMsg.toString)
    errorWindow.setCaretPosition(0)
    outLayout.show(outPanel, "Error")
    // For the case where a warning is sent to the regular Output window
    Utils.schedule(0.9) { outLayout.show(outPanel, "Error") }
  }

  def showOutput(outText: String) {
    Utils.runInSwingThread {
      showOutputHelper(outText, outputColor)
    }
    lastOutput = outText
  }

  def showOutput(outText: String, color: Color) {
    Utils.runInSwingThread {
      showOutputHelper(outText, color)
    }
    lastOutput = outText
  }

  def showOutputHelper(outText: String, color: Color): Unit = {
    appendOutput(outText, color)
    enableClearButton()
  }

  def showErrorMsg(errMsg: String) {
    Utils.runInSwingThread {
      appendError(errMsg)
      enableClearButton()
    }
    //    lastOutput = errMsg
  }

  def showErrorText(errText: String) {
    Utils.runInSwingThread {
      appendError(errText)
      enableClearButton()
    }
    //    lastOutput = errText
  }

  def showSmartErrorText(errText: String, line: Int, column: Int, offset: Int) {
    Utils.runInSwingThread {
      appendError(errText, Some(offset))
      enableClearButton()
    }
    //    lastOutput = errText
  }

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
    stopAnimation()
  }

  def stopInterpreter() {
    codeRunner.interruptInterpreter()
  }

  def stopAnimation() {
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
      maybeOutputDelimiter()
    }
    historyManager.codeRun(code)
  }

  def maybeOutputDelimiter() {
    if (lastOutput.length > 0 && !lastOutput.endsWith(OutputDelimiter))
      showOutput(OutputDelimiter, promptColor)
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

  private var openedFile: Option[File] = None
  private var fileData: String = _
  private def saveFileData(d: String) {
    fileData = removeWorksheetOutput(d)
  }
  private def fileChanged = fileData != removeWorksheetOutput(codePane.getText)

  def hasOpenFile = openedFile.isDefined

  def openFileWithoutClose(file: java.io.File) {
    import util.RichFile._
    val script = file.readAsString
    codePane.setText(Utils.stripCR(script))
    codePane.setCaretPosition(0)
    openedFile = Some(file)
    kojoCtx.fileOpened(file)
    saveFileData(script)
  }

  def openFile(file: java.io.File) {
    try {
      closeFileIfOpen()
      openFileWithoutClose(file)
    }
    catch {
      case e: RuntimeException =>
    }
  }

  def closeFileIfOpen() {
    if (openedFile.isDefined) {
      if (fileChanged) {
        val doSave = JOptionPane.showConfirmDialog(
          null,
          Utils.loadString("S_FileChanged") format (openedFile.get.getName, openedFile.get.getName))
        if (doSave == JOptionPane.CANCEL_OPTION || doSave == JOptionPane.CLOSED_OPTION) {
          throw new RuntimeException("Cancel File Close")
        }
        if (doSave == JOptionPane.YES_OPTION) {
          saveFile()
        }
      }
      openedFile = None
      kojoCtx.fileClosed()
    }
  }

  def closeFileAndClrEditorIgnoringCancel() {
    try {
      closeFileAndClrEditor()
    }
    catch {
      case e: RuntimeException => // ignore user cancel
    }
  }

  def closeFileAndClrEditor() {
    closeFileIfOpen() // can throw runtime exception if user cancels
    this.codePane.setText(null)
    clearSButton.setEnabled(false)
    codePane.requestFocusInWindow
  }

  def saveFile() {
    saveTo(openedFile.get)
    kojoCtx.fileSaved()
  }

  import java.io.File
  private def saveTo(file: File) {
    import util.RichFile._
    val script = codePane.getText()
    file.write(script)
    saveFileData(script)
  }

  def saveAs(file: java.io.File) {
    if (file.exists) {
      val doSave = JOptionPane.showConfirmDialog(
        null,
        Utils.loadString("S_FileExists") format (file.getName))
      if (doSave == JOptionPane.CANCEL_OPTION || doSave == JOptionPane.CLOSED_OPTION) {
        throw new RuntimeException("Cancel File SaveAs")
      }
      else if (doSave == JOptionPane.NO_OPTION) {
        throw new IllegalArgumentException("Redo 'Save As' to select new file")
      }
      else if (doSave == JOptionPane.YES_OPTION) {
        saveTo(file)
      }
    }
    else {
      saveTo(file)
    }
  }

  def closing() {
    if (openedFile.isDefined) {
      closeFileIfOpen()
    }
    //    else {
    //      if (codePane.getText.size > 0) {
    //        val doSave = JOptionPane.showConfirmDialog(
    //          kojoCtx.frame,
    //          "You have unsaved work. Do you want to save your script to a file?")
    //        if (doSave == JOptionPane.CANCEL_OPTION || doSave == JOptionPane.CLOSED_OPTION) {
    //          throw new RuntimeException("Veto Shutdown")
    //        }
    //        else if (doSave == JOptionPane.YES_OPTION) {
    //          kojoCtx.saveAsFile()
    //        }
    //      }
    //    }
  }

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

  def runCodeWithOutputCapture(): String = {
    runMonitor = new OutputCapturingRunner()
    val ret = runMonitor.asInstanceOf[OutputCapturingRunner].go()
    runMonitor = new NoOpRunMonitor()
    ret
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

  class OutputCapturingRunner extends RunMonitor {
    val outputx: StringBuilder = new StringBuilder()
    val latch = new CountDownLatch(1)

    def reportOutput(outText: String) = captureOutput(outText)
    def onRunStart() {}
    def onRunEnd() = latch.countDown()

    def go(): String = {
      runCode()
      latch.await()
      outputx.toString
    }

    def captureOutput(output: String) {
      outputx.append(output)
    }
  }

  class StatusStrip extends JPanel {
    val ErrorColor = new Color(0xff1a1a) // reddish
    val SuccessColor = new Color(0x33ff33) // greenish
    val NeutralColor = new Color(0xf0f0f0) // very light gray
    val StripWidth = 6

    setBackground(NeutralColor)
    setPreferredSize(new Dimension(StripWidth, 10))

    def linkToPane() {
      codePane.getDocument.addDocumentListener(new DocumentListener {
        def insertUpdate(e: DocumentEvent) = onDocChange()
        def removeUpdate(e: DocumentEvent) = onDocChange()
        def changedUpdate(e: DocumentEvent) {}
      })
    }

    def onSuccess() {
      setBackground(SuccessColor)
    }

    def onError() {
      setBackground(ErrorColor)
    }

    def onDocChange() {
      if (imanip.isEmpty) {
        if (getBackground != NeutralColor) setBackground(NeutralColor)
      }
      else {
        if (!imanip.get.inSliderChange) {
          imanip.get.close()
        }
      }
    }
  }
}

trait RunMonitor {
  def reportOutput(outText: String)
  def onRunStart()
  def onRunEnd()
}

class NoOpRunMonitor extends RunMonitor {
  def reportOutput(outText: String) {}
  def onRunStart() {}
  def onRunEnd() {}
}
