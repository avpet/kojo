val pageStyle = "background-color:#93989c; margin:5px;font-size:small;"
val centerStyle = "text-align:center;"
val headerStyle = "text-align:center;font-size:95%;color:#fafafa;font-weight:bold;"
val codeStyle = "background-color:#4a6cd4;margin-top:3px"
val linkStyle = "color:#fafafa"
val codeLinkStyle = "text-decoration:none;font-size:x-small;color:#fafafa;"
val footerStyle = "font-size:90%;margin-top:15px;color:1a1a1a;"
val helpStyle = "background-color:#ffffcc;margin:10px;"

val Turtle = "t"
val Pictures = "p"
val PictureXforms = "pt"
val ControlFlow = "cf"
val Abstraction = "a"
val Conditions = "c"

def navLinks =
    <div style={ headerStyle }>
        <a style={ linkStyle } href={ "http://localpage/" + Turtle }>Turtle</a> | <a style={ linkStyle } href={ "http://localpage/" + Pictures }>Picture</a> <br/>
        <a style={ linkStyle } href={ "http://localpage/" + PictureXforms }>Picture Transforms</a> <br/>
        <a style={ linkStyle } href={ "http://localpage/" + ControlFlow }>Flow</a> | <a style={ linkStyle } href={ "http://localpage/" + Conditions }>Condition</a> <br/>
        <a style={ linkStyle } href={ "http://localpage/" + Abstraction }>Abstraction</a> <br/>
        <hr/>
        <br/>
    </div>

def footer =
    <div style={ footerStyle }>
        Click on any instruction to insert it into the Script Editor at the current Caret location.
    </div>

import scala.collection.mutable.LinkedHashMap

val tTemplates = LinkedHashMap(
    "clear()" -> "clear()",
    "invisible()" -> "invisible()",
    "cleari()" -> "cleari()",
    "setAnimationDelay(d)" -> "setAnimationDelay(100)",
    "forward(n)" -> "forward(50)",
    "right(a)" -> "right(90)",
    "left(a)" -> "left(90)",
    "setPenColor(c)" -> "setPenColor(blue)",
    "setFillColor(c)" -> "setFillColor(blue)",
    "setBackground(c)" -> "setBackground(yellow)",
    "setPenThickness(t)" -> "setPenThickness(4)",
    "penUp()" -> "penUp()",
    "penDown()" -> "penDown()",
    "write(t)" -> """write("Hi There")""",
    "setPenFontSize(n)" -> "setPenFontSize(18)",
    "" -> "",
    "setPosition(x,y)" -> "setPosition(10, 10)",
    "position" -> "position",
    "moveTo(x,y)" -> "moveTo(50, 50)"
)

val cfTemplates = LinkedHashMap(
    "repeat(n) {cmds}" -> """repeat (4) {
    forward(50)
    right()
}""",
    "if" -> """if (true) {
    setPenColor(blue)
}""",
    "if-else" -> """if (true) {
    setPenColor(blue)
}
else {
    setPenColor(green)
}""",
    "for (cmd)" -> """for (i <- 1 to 4) {
    println(i)
}"""
)

val aTemplates = LinkedHashMap(
    "val" -> "val x = 10",
    "def (cmd)" -> """def square(n: Int) {
    repeat (4) {
        forward(50)
        right()
    }
} """,
    "def (fn)" -> """def max(n1: Int, n2: Int) = 
        if (n1 > n2) n1 else n2"""
)

val pTemplates = LinkedHashMap(
    "Picture" -> """Picture {
    forward(50)    
}""",
    "picRow(pics)" -> "picRow(PicShape.hline(50), PicShape.vline(50))",
    "picCol(pics)" -> "picCol(PicShape.vline(50), PicShape.hline(50))",
    "picStack(pics)" -> "picStack(PicShape.hline(50), PicShape.vline(50))",
    "draw(pics)" -> "draw(PicShape.hline(50), PicShape.vline(50))",
    "" -> "",
    "PicShape.hline(len)" -> "PicShape.hline(50)",
    "PicShape.vline(len)" -> "PicShape.vline(50)",
    "PicShape.rect(h, w)" -> "PicShape.rect(50, 100)",
    "PicShape.circle(r)" -> "PicShape.circle(50)",
    "PicShape.text(s, n)" -> """PicShape.text("Hello", 18)"""
)

val ptTemplates = LinkedHashMap(
    "rot(a)" -> "rot(45)",
    "scale(f)" -> "scale(2)",
    "trans(x,y)" -> "trans(10, 10)",
    "penColor(c)" -> "penColor(blue)",
    "fillColor(c)" -> "fillColor(blue)",
    "penWidth(w)" -> "penWidth(4)",
    "hue(f)" -> "hue(0.1)",
    "sat(f)" -> "sat(0.1)",
    "brit(f)" -> "brit(0.1)",
    "opac(f)" -> "opac(0.1)",
    "flipX" -> "flipX",
    "flipY" -> "flipY",
    "axes" -> "axes"
)

val cTemplates = LinkedHashMap(
    "==" -> "2 == 2",
    "!=" -> "1 != 2",
    ">" -> "2 > 1",
    "<" -> "1 < 2",
    ">=" -> "2 >= 1",
    "<=" -> "1 <= 2"
)

val instructions = Map(
    "t" -> tTemplates.keys.toIndexedSeq,
    "cf" -> cfTemplates.keys.toIndexedSeq,
    "a" -> aTemplates.keys.toIndexedSeq,
    "p" -> pTemplates.keys.toIndexedSeq,
    "pt" -> ptTemplates.keys.toIndexedSeq,
    "c" -> cTemplates.keys.toIndexedSeq
)

val templates = Map(
    "t" -> tTemplates,
    "cf" -> cfTemplates,
    "a" -> aTemplates,
    "p" -> pTemplates,
    "pt" -> ptTemplates,
    "c" -> cTemplates
)

def runLink(category: String, n: Int) = s"http://runhandler/$category/$n"
def code(category: String, n: Int) =
    <div style={ codeStyle }> 
        <pre><code><a href={ runLink(category, n) } style={ codeLinkStyle }> { instructions(category)(n) }</a></code></pre>
    </div>

def pageFor(cat: String) = Page(
    name = cat,
    body =
        <body style={ pageStyle }>
        { navLinks }
        { for (i <- 0 until instructions(cat).length) yield (if (instructions(cat)(i) == "") <br/> else code(cat, i)) }
        { footer }
        </body>,
    code = {
        stAddUiComponent(footerPanel)
    }
)

val story = Story(
    pageFor(Turtle),
    pageFor(ControlFlow),
    pageFor(Abstraction),
    pageFor(Pictures),
    pageFor(PictureXforms),
    pageFor(Conditions)
)
stClear()
stSetStorytellerWidth(50)

import javax.swing._
import java.awt.event._
@volatile var helpFrame: JWindow = _
@volatile var helpPane: JEditorPane = _
@volatile var footerPanel: JPanel = _
@volatile var helpOn = true

runInGuiThread {
    helpFrame = new JWindow(stFrame)
    helpFrame.setBounds(300, 100, 500, 300)
    helpPane = new JEditorPane
    helpPane.setBackground(Color(255, 255, 51))
    helpPane.setContentType("text/html")
    helpPane.setEditable(false)
    val helpScroller = new JScrollPane(helpPane)
    helpScroller.setBorder(BorderFactory.createLineBorder(gray, 1))
    helpFrame.getContentPane.add(helpScroller)
    helpPane.addFocusListener(new FocusAdapter {
        override def focusLost(e: FocusEvent) = schedule(0.3) {
            if (!helpPane.isFocusOwner) { // make Linux work
                helpFrame.setVisible(false)
            }
        }
    })

    footerPanel = new JPanel
    footerPanel.setBackground(color(0x93989c))
    val helpLabel = new JLabel("Live Help"); helpLabel.setForeground(color(0xfafafa))
    footerPanel.add(helpLabel)
    val onButton = new JRadioButton("On"); onButton.setForeground(color(0xfafafa))
    onButton.setSelected(true)
    val offButton = new JRadioButton("Off"); offButton.setForeground(color(0xfafafa))
    offButton.setSelected(false)
    val onOff = new ButtonGroup; onOff.add(onButton); onOff.add(offButton)
    footerPanel.add(onButton)
    footerPanel.add(offButton)
    onButton.addActionListener(new ActionListener {
        override def actionPerformed(e: ActionEvent) {
            helpOn = true
        }
    })
    offButton.addActionListener(new ActionListener {
        override def actionPerformed(e: ActionEvent) {
            helpOn = false
        }
    })
}

def insertCode(cat: String, idx: Int) {
    stInsertCode(templates(cat)(instructions(cat)(idx)))
    helpFrame.setVisible(false)
}
def smartInsertCode(cat: String, idx: Int) {
    stSmartInsertCode(templates(cat)(instructions(cat)(idx)))
    helpFrame.setVisible(false)
}

stAddLinkHandler(Turtle, story) { idx: Int => smartInsertCode(Turtle, idx) }
stAddLinkHandler(ControlFlow, story) { idx: Int => smartInsertCode(ControlFlow, idx) }
stAddLinkHandler(Pictures, story) { idx: Int => smartInsertCode(Pictures, idx) }
stAddLinkHandler(PictureXforms, story) { idx: Int => insertCode(PictureXforms, idx) }
stAddLinkHandler(Abstraction, story) { idx: Int => smartInsertCode(Abstraction, idx) }
stAddLinkHandler(Conditions, story) { idx: Int => insertCode(Conditions, idx) }

def keyFor(cat: String, n: Int) = {
    instructions(cat)(n).takeWhile(c => c != '(' && c != '-').trim
}

def showHelp(cat: String, idx: Int) {
    if (helpOn) {
        helpPane.setText(s"""<body style="$helpStyle">
        ${stHelpFor(keyFor(cat, idx))}
        </body>
        """
        )
        helpPane.setCaretPosition(0)
        helpFrame.setVisible(true)
        // try to make sure that the help pane gains focus
        helpPane.requestFocus()
        helpPane.requestFocusInWindow()
    }
}

stAddLinkEnterHandler(Turtle, story) { idx: Int => showHelp(Turtle, idx) }
stAddLinkEnterHandler(ControlFlow, story) { idx: Int => showHelp(ControlFlow, idx) }
stAddLinkEnterHandler(Pictures, story) { idx: Int => showHelp(Pictures, idx) }
stAddLinkEnterHandler(PictureXforms, story) { idx: Int => showHelp(PictureXforms, idx) }
stAddLinkEnterHandler(Abstraction, story) { idx: Int => showHelp(Abstraction, idx) }
stAddLinkEnterHandler(Conditions, story) { idx: Int => showHelp(Conditions, idx) }

stOnStoryStop(story) {
    helpFrame.setVisible(false)
    helpFrame.dispose()
}
stPlayStory(story)
runInBackground { stSetScript("") }
