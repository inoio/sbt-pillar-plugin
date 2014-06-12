package io.ino.sbtpillar

import sbt._
import Keys._
import scala.Some

object Plugin extends sbt.Plugin {

  object PillarKeys {
    val createKeyspace = taskKey[Unit]("Create keyspace.")
    val dropKeyspace = taskKey[Unit]("Drop keyspace.")
    val migrate = taskKey[Unit]("Run pillar migrations.")
    val cleanMigrate = taskKey[Unit]("Recreate keyspace and run pillar migrations.")

    val pillarConfigFile = settingKey[File]("Path to the configuration file holding the cassandra uri")
    val pillarConfigKey = settingKey[String]("Configuration key storing the cassandra url")
    val pillarMigrationsDir = settingKey[File]("Path to the directory holding migration files")
  }

  import PillarKeys._
  import Pillar.{withCassandraUrl, withSession}
  import com.datastax.driver.core.Session

  def pillarSettings: Seq[sbt.Def.Setting[_]] = Seq(
    createKeyspace := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value, streams.value.log) { url =>
        streams.value.log.info(s"Creating keyspace ${url.keyspace} at ${url.hosts(0)}:${url.port}")
        Pillar.initialize(url.keyspace, url.hosts(0), url.port)
      }
    },
    dropKeyspace := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value, streams.value.log) { url =>
        streams.value.log.info(s"Dropping keyspace ${url.keyspace} at ${url.hosts(0)}:${url.port}")
        Pillar.destroy(url.keyspace, url.hosts(0), url.port)
      }
    },
    migrate := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value, streams.value.log) { url =>
        val dir = pillarMigrationsDir.value
        streams.value.log.info(s"Migrating keyspace ${url.keyspace} at ${url.hosts(0)}:${url.port} using migrations in $dir")
        Pillar.migrate(url.keyspace, url.hosts(0), url.port, dir)
      }
    },
    cleanMigrate := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value, streams.value.log) { url =>
        val host = url.hosts(0)

        withSession(url, streams.value.log) { (url, session) =>
          streams.value.log.info(s"Dropping keyspace ${url.keyspace} at $host:${url.port}")
          session.execute(s"DROP KEYSPACE IF EXISTS ${url.keyspace}")
        }

        streams.value.log.info(s"Creating keyspace ${url.keyspace} at $host:${url.port}")
        Pillar.initialize(url.keyspace, host, url.port)

        val dir = pillarMigrationsDir.value
        streams.value.log.info(s"Migrating keyspace ${url.keyspace} at $host:${url.port} using migrations in $dir")
        Pillar.migrate(url.keyspace, url.hosts(0), url.port, dir)
      }
    },
    pillarConfigKey := "cassandra.url",
    pillarConfigFile := file("conf/application.conf"),
    pillarMigrationsDir := file("conf/migrations")
  )

  private case class CassandraUrl(hosts: Seq[String], port: Int, keyspace: String)

  private object Pillar {

    import com.datastax.driver.core.Cluster
    import com.streamsend.pillar._
    import com.typesafe.config.ConfigFactory
    import java.nio.file.Files
    import scala.util.control.NonFatal

    def withCassandraUrl(configFile: File, configKey: String, logger: Logger)(block: CassandraUrl => Unit): Unit = {

      logger.info(s"Reading config from ${configFile.getAbsolutePath}")
      val urlString = ConfigFactory.parseFile(configFile).resolve().getString(configKey)
      val url = parseUrl(urlString)
      try {
        block(url)
      } catch {
        case NonFatal(e) =>
          logger.error(s"An error occurred while performing task: $e")
          logger.trace(e)
      }
    }

    def withSession(url: CassandraUrl, logger: Logger)(block: (CassandraUrl, Session) => Unit): Unit = {
      val cluster = new Cluster.Builder().addContactPoints(url.hosts.toArray: _*).withPort(url.port).build
      try {
        val session = cluster.connect
        block(url, session)
      } catch {
        case NonFatal(e) =>
          logger.error(s"An error occurred while performing task: $e")
          logger.trace(e)
      } finally {
        cluster.closeAsync()
      }
    }

    def initialize(keyspace: String, host: String, port: Int): Unit = {
      val dataStore = DataStore("faker", keyspace, host)
      Migrator(Registry(Seq.empty)).initialize(dataStore)
    }

    def destroy(keyspace: String, host: String, port: Int): Unit = {
      val dataStore = DataStore("faker", keyspace, host)
      Migrator(Registry(Seq.empty)).destroy(dataStore)
    }

    def migrate(keyspace: String, host: String, port: Int, migrationsDir: File): Unit = {
      val registry = Registry(loadMigrations(migrationsDir))
      val dataStore = DataStore("faker", keyspace, host)
      Migrator(registry).migrate(dataStore)
    }

    private def parseUrl(urlString: String): CassandraUrl = {
      val uri = new URI(urlString)
      val additionalHosts = Option(uri.getQuery) match {
        case Some(query) => query.split('&').map(_.split('=')).filter(param => param(0) == "host").map(param => param(1)).toSeq
        case None => Seq.empty
      }
      CassandraUrl(Seq(uri.getHost) ++ additionalHosts, uri.getPort, uri.getPath.substring(1))
    }

    private def loadMigrations(migrationsDir: File) = {
      val parser = com.streamsend.pillar.Parser()
      Option(migrationsDir.listFiles()) match {
        case Some(files) => files.map { f =>
            val in = Files.newInputStream(f.toPath)
            try {
              parser.parse(in)
            } finally {
              in.close()
            }
          }.toList
        case None => throw new IllegalArgumentException("The pillarMigrationsDir does not contain any migration files - wrong configuration?")
      }
    }

  }

}