sbtPlugin := true

name := "sbt-pillar-plugin"

description := "sbt plugin for cassandra schema/data migrations using pillar (https://github.com/comeara/pillar)"

organization := "io.ino"

version := "1.0.0-RC1"

licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalaVersion := "2.10.4"

scalacOptions += "-target:jvm-1.7"

// pillar intermediately published at bintray, while waiting for
// an official release (see https://github.com/comeara/pillar/issues/12)
resolvers += "bintray magro" at "http://dl.bintray.com/magro/maven"

libraryDependencies ++= Seq(
  "com.streamsend" %% "pillar" % "1.0.3-RC1",
  "org.slf4j" % "slf4j-api" % "1.7.7"
)

// Maven publishing info
publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
  <url>https://github.com/inoio/sbt-pillar-plugin</url>
  <scm>
    <url>git@github.com:inoio/sbt-pillar-plugin.git</url>
    <connection>scm:git:git@github.com:inoio/sbt-pillar-plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>martin.grotzke</id>
      <name>Martin Grotzke</name>
      <url>https://github.com/magro</url>
    </developer>
  </developers>)