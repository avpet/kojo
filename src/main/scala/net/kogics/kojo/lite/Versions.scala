package net.kogics.kojo.lite

object Versions {
    val KojoVersion = "070213-2"
    val JavaVersion = geogebra.main.AppD.getJavaVersion
    val ScalaVersion = scala.tools.nsc.Properties.versionString.substring("version ".length)
}