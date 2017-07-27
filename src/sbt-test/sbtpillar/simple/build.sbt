import io.ino._
import io.ino.sbtpillar.Plugin.PillarKeys._

lazy val root = (project in file("."))
  .settings(
    version := "0.1",
    scalaVersion := "2.10.6",
    sbtpillar.Plugin.pillarSettings ++ Seq(
      pillarReplicationFactorConfigKey := "cassandra.replicationFactor",
      pillarDefaultConsistencyLevelConfigKey := "cassandra.defaultConsistencyLevel"
    )
  )

