import _root_.io.ino._
import _root_.io.ino.sbtpillar.Plugin.PillarKeys._

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.10.6",
    sbtpillar.Plugin.pillarSettings
  )

