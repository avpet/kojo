package net.kogics.kojo.xscala

object CodeTemplates {
  val templates = Map(
    "pre" -> """<pre>
    ${cursor}
</pre>
""",
    "incr" -> """IncrPage(
    name = "",
    style = "",
    body = List(
        Para(
            <p>
                ${cursor}
            </p>
        ),
        Para(
            <p>
                
            </p>
        )
    )
)
""",
    "melody" -> """Melody("${cursor}", "")""",
    "mscore" -> """MusicScore(
    Melody("${cursor}", ""),
    Rhythm("", "", "")
)
""",
    "rythm" -> """Rhythm("${cursor}", "q", "o")""",
    "page" -> """Page(
    name = "",
    body =
        <body>
            ${cursor}
        </body>,
    code = {}
)
""",
    "para" -> """Para(
    <p>
        ${cursor}
    </p>,
    code = {}
)
""",
    "a" -> """<a href="${cursor}"></a>""",
    "br" -> "<br/>${cursor} ",
    "code" -> "<code>${cursor}</code>",
    "div" -> """<div>
    ${cursor}
</div>
""",
    "em" -> "<em>${cursor}</em>",
    "li" -> "<li>${cursor}</li>",
    "p" -> """<p>
    ${cursor}
</p>
""",
    "span" -> "<span>${cursor}</span>",
    "strong" -> "<strong>${cursor}</strong>",
    "sty" -> """style="${cursor}" """,
    "ul" -> """<ul>
    <li>${cursor}</li>
    <li></li>
</ul>
""")

  def apply(name: String) = templates(name)
  def asString(name: String) = xml.Utility.escape(templates(name).replace("${cursor}", "|"))
  def beforeCursor(name: String) = templates(name).split("""\$\{cursor\}""")(0)
  def afterCursor(name: String) = templates(name).split("""\$\{cursor\}""")(1)
}