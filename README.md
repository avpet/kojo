**Kojo Links**

* The [Kojo home-page][1] provides user-level information about Kojo.
* The [Kojo issue-tracker][3] let's you file bug reports.
* The [Kojo Localization][5] page tells you how to translate Kojo to your language.

**To start hacking:**

* Fork the repo (i.e. create a server-clone), and then clone your fork (using the `hg clone` command) to create a local Kojo workspace.
* Make sure you have Java 6 on your path. You need Java 6 to build Kojo.  You can run Kojo with Java 6, Java 7, or Java 8.
    * [Download JDK 1.6][4] (if you don't already have it).
* Copy `javaws.jar` and `deploy.jar` from your `jre/lib` directory into the `lib` directory in your Kojo workspace. These jar files are required to compile the Kojo Webstart launcher.
* Run `./sbt.sh clean package` to build Kojo.
* Run `./sbt.sh test` to run the Kojo unit tests.
* Run `./sbt.sh run` to run Kojo (use `net.kogics.kojo.lite.DesktopMain` as the main class)
* Run `./sbt.sh eclipse` or `./sbt.sh gen-idea` to generate project files for Eclipse or IDEA (you should be able to do something similar for Netbeans after installing the sbt-netbeans plugin). Import the newly generated project into your IDE, and start hacking! For running Kojo from within the IDE, the main class is `net.kogics.kojo.lite.DesktopMain`. For debugging, the main class is `net.kogics.kojo.lite.Main`. 

**Eclipse Notes**:
You need to tweak the Eclipse project generated by sbt. Right-click on the project in Eclipse, bring up *Properties*, go to *Java Build Path*, and then go to *Libraries*. Remove the *Scala Library* and *Scala Compiler* containers, and add the Scala library and compiler jars (from your local Scala install, or cached sbt jars). Your project *Libraries* should now contain the following Scala jars:

* scala-library.jar
* scala-compiler.jar
* scala-reflect.jar
* scala-actors-xx.jar
* scala-parser-combinators-xx.jar
* scala-xml-xx.jar
* scala-swing-xx.jar
 
Also make sure that the *JRE System Library* used by the project is at the JDK 1.6 level.

  [1]: http://www.kogics.net/kojo
  [3]: https://bitbucket.org/lalit_pant/kojo/issues?status=new&status=open
  [4]: http://www.oracle.com/technetwork/java/javase/downloads/java-archive-downloads-javase6-419409.html
  [5]: https://bitbucket.org/lalit_pant/kojo/wiki/Kojo%20Localization