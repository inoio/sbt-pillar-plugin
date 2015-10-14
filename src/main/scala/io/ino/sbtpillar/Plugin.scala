package io.ino.sbtpillar

import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ConsistencyLevel, QueryOptions}
import sbt.Keys._
import sbt._

import scala.util.Try

object Plugin extends sbt.Plugin {

  object PillarKeys {
    val createKeyspace = taskKey[Unit]("Create keyspace.")
    val dropKeyspace = taskKey[Unit]("Drop keyspace.")
    val migrate = taskKey[Unit]("Run pillar migrations.")
    val cleanMigrate = taskKey[Unit]("Recreate keyspace and run pillar migrations.")

    val pillarConfigFile = settingKey[File]("Path to the configuration file holding the cassandra uri")
    val pillarConfigKey = settingKey[String]("Configuration key storing the cassandra url")
    val pillarDefaultConsistencyLevelConfigKey = settingKey[String]("Configuration key storing the consistency level for the session")
    val pillarReplicationStrategyConfigKey = settingKey[String]("Configuration key storing the replication strategy to create keyspaces with")
    val pillarReplicationFactorConfigKey = settingKey[String]("Configuration key storing the replication factor to create keyspaces with")
    val pillarMigrationsDir = settingKey[File]("Path to the directory holding migration files")
  }

  import com.datastax.driver.core.Session
  import io.ino.sbtpillar.Plugin.Pillar.{withCassandraUrl, withSession}
  import io.ino.sbtpillar.Plugin.PillarKeys._

  private def taskSettings: Seq[sbt.Def.Setting[_]] = Seq(
    createKeyspace := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value,
        pillarReplicationStrategyConfigKey.value, pillarReplicationFactorConfigKey.value,
        pillarDefaultConsistencyLevelConfigKey.value,
        streams.value.log) { (url, replicationStrategy, replicationFactor, defaultConsistencyLevel) =>
        streams.value.log.info(s"Creating keyspace ${url.keyspace} at ${url.hosts(0)}:${url.port}")
        Pillar.initialize(replicationStrategy, replicationFactor, url, streams.value.log)
      }
    },
    dropKeyspace := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value,
        pillarReplicationStrategyConfigKey.value, pillarReplicationFactorConfigKey.value,
        pillarDefaultConsistencyLevelConfigKey.value,
        streams.value.log) { (url, replicationStrategy, replicationFactor, defaultConsistencyLevel) =>
        streams.value.log.info(s"Dropping keyspace ${url.keyspace} at ${url.hosts(0)}:${url.port}")
        Pillar.destroy(url, streams.value.log)
      }
    },
    migrate := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value,
        pillarReplicationStrategyConfigKey.value, pillarReplicationFactorConfigKey.value,
        pillarDefaultConsistencyLevelConfigKey.value,
        streams.value.log) { (url, replicationStrategy, replicationFactor, defaultConsistencyLevel) =>
        val migrationsDir = pillarMigrationsDir.value
        streams.value.log.info(s"Migrating keyspace ${url.keyspace} at ${url.hosts(0)}:${url.port} using migrations in $migrationsDir with consistency $defaultConsistencyLevel")
        Pillar.migrate(migrationsDir, url, defaultConsistencyLevel, streams.value.log)
      }
    },
    cleanMigrate := {
      withCassandraUrl(pillarConfigFile.value, pillarConfigKey.value,
        pillarReplicationStrategyConfigKey.value, pillarReplicationFactorConfigKey.value,
        pillarDefaultConsistencyLevelConfigKey.value,
        streams.value.log) { (url, replicationStrategy, replicationFactor, defaultConsistencyLevel) =>
        val host = url.hosts(0)

        withSession(url, Some(defaultConsistencyLevel), streams.value.log) { (url, session) =>
          streams.value.log.info(s"Dropping keyspace ${url.keyspace} at $host:${url.port}")
          session.execute(s"DROP KEYSPACE IF EXISTS ${url.keyspace}")

          Pillar.checkPeerSchemaVersions(session, streams.value.log)

          streams.value.log.info(s"Creating keyspace ${url.keyspace} at $host:${url.port}")
          Pillar.initialize(session, replicationStrategy, replicationFactor, url)

          val dir = pillarMigrationsDir.value
          streams.value.log.info(s"Migrating keyspace ${url.keyspace} at $host:${url.port} using migrations in $dir with consistency $defaultConsistencyLevel")
          Pillar.migrate(session, dir, url)
        }

      }
    },
    pillarConfigKey := "cassandra.url",
    pillarReplicationStrategyConfigKey := "cassandra.replicationStrategy",
    pillarReplicationFactorConfigKey := "cassandra.replicationFactor",
    pillarDefaultConsistencyLevelConfigKey := "cassandra.defaultConsistencyLevel",
    pillarConfigFile := file("conf/application.conf"),
    pillarMigrationsDir := file("conf/migrations")
  )

  def pillarSettings: Seq[sbt.Def.Setting[_]] = inConfig(Test)(taskSettings) ++ taskSettings

  private case class CassandraUrl(hosts: Seq[String], port: Int, keyspace: String)

  private object Pillar {

    import java.nio.file.Files

    import com.chrisomeara.pillar._
    import com.datastax.driver.core.Cluster
    import com.typesafe.config.ConfigFactory

    import scala.util.control.NonFatal

    private val DEFAULT_DEFAULT_CONSISTENCY_LEVEL = ConsistencyLevel.QUORUM
    private val DEFAULT_REPLICATION_STRATEGY = "SimpleStrategy"
    private val DEFAULT_REPLICATION_FACTOR = 3

    def withCassandraUrl(configFile: File,
                         configKey: String,
                         repStrategyConfigKey: String,
                         repFactorConfigKey: String,
                         defaultConsistencyLevelConfigKey: String,
                         logger: Logger)(block: (CassandraUrl, String, Int, ConsistencyLevel) => Unit): Unit = {
      val configFileMod = file(sys.env.getOrElse("PILLAR_CONFIG_FILE", configFile.getAbsolutePath))
      logger.info(s"Reading config from ${configFileMod.getAbsolutePath}")
      val config = ConfigFactory.parseFile(configFileMod).resolve()
      val urlString = config.getString(configKey)
      val url = parseUrl(urlString)

      val defaultConsistencyLevel = Try(ConsistencyLevel.valueOf(config.getString(defaultConsistencyLevelConfigKey))).getOrElse(DEFAULT_DEFAULT_CONSISTENCY_LEVEL)
      val replicationStrategy = Try(config.getString(repStrategyConfigKey)).getOrElse(DEFAULT_REPLICATION_STRATEGY)
      val replicationFactor = Try(config.getInt(repFactorConfigKey)).getOrElse(DEFAULT_REPLICATION_FACTOR)
      try {
        block(url, replicationStrategy, replicationFactor, defaultConsistencyLevel)
      } catch {
        case NonFatal(e) =>
          logger.error(s"An error occurred while performing task: $e")
          logger.trace(e)
          throw e
      }
    }

    def withSession(url: CassandraUrl, defaultConsistencyLevel: Option[ConsistencyLevel], logger: Logger)
                   (block: (CassandraUrl, Session) => Unit): Unit = {

      implicit val iLog = logger

      val queryOptions = new QueryOptions()
      defaultConsistencyLevel.foreach(queryOptions.setConsistencyLevel)
      val cluster = new Cluster.Builder()
        .addContactPointsSafe(url.hosts.toArray: _*)
        .withPort(url.port)
        .withQueryOptions(queryOptions)
        .build
      try {
        val session = cluster.connect
        block(url, session)
      } catch {
        case NonFatal(e) =>
          logger.error(s"An error occurred while performing task: $e")
          logger.trace(e)
          throw e
      } finally {
        cluster.closeAsync()
      }
    }

    def initialize(replicationStrategy: String, replicationFactor: Int, url: CassandraUrl, logger: Logger): Unit = {
      withSession(url, None, logger) { (url, session) =>
        initialize(session, replicationStrategy, replicationFactor, url)
      }
    }

    def initialize(session: Session, replicationStrategy: String, replicationFactor: Int, url: CassandraUrl) {
      Migrator(Registry(Seq.empty)).initialize(session, url.keyspace, replicationOptionsWith(replicationStrategy, replicationFactor))
    }

    def destroy(url: CassandraUrl, logger: Logger): Unit = {
      withSession(url, None, logger) { (url, session) =>
        Migrator(Registry(Seq.empty)).destroy(session, url.keyspace)
      }
    }

    def migrate(migrationsDir: File, url: CassandraUrl, defaultConsistencyLevel: ConsistencyLevel, logger: Logger): Unit = {
      withSession(url, Some(defaultConsistencyLevel), logger) { (url, session) =>
        migrate(session, migrationsDir, url)
      }
    }

    def migrate(session: Session, migrationsDir: File, url: CassandraUrl) {
      val registry = Registry(loadMigrations(migrationsDir))
      session.execute(s"USE ${url.keyspace}")
      Migrator(registry).migrate(session)
    }

    def checkPeerSchemaVersions(session: Session, logger: Logger): Unit = {
      import scala.collection.JavaConversions._
      val schemaByPeer = session.execute(select("peer", "schema_version").from("system", "peers")).all().map { row =>
        (row.getInet("peer"), row.getUUID("schema_version"))
      }.toMap

      if(schemaByPeer.values.toSet.size > 1) {
        val peerSchemaVersions = schemaByPeer.map{ case (peer, schemaVersion) => s"peer: $peer, schema_version: $schemaVersion" }.mkString("\n")
        logger.warn(s"There are peers with different schema versions:\n$peerSchemaVersions")
      }
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
      val parser = com.chrisomeara.pillar.Parser()
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

    private def replicationOptionsWith(replicationStrategy: String, replicationFactor: Int) =
      new ReplicationOptions(Map("class" -> replicationStrategy, "replication_factor" -> replicationFactor))


    private implicit class RichClusterBuilder(builder: Cluster.Builder) {

      /** Add contact points ignoring errors for single contact points.
        * Cluster.Builder.addContactPoints fails/throws as soon as a single node/host address cannot be resolved via InetAddress.
        */
      def addContactPointsSafe(addresses: String*)(implicit logger: Logger): Cluster.Builder = {

        require(addresses.nonEmpty, "At least 1 contact point must be specified.")

        val (built, exceptions) = addresses.foldLeft((builder, List.empty[Throwable])) { case ((b, es), address) =>
          try {
            (b.addContactPoint(address), es)
          } catch {
            case e: Throwable =>
              logger.warn(s"Failed to add contact point $address: $e")
              (b, e :: es)
          }
        }

        if(exceptions.length == addresses.length) {
          logger.error(s"All contact points failed on addContactPoint, rethrowing exception for last contact point.")
          throw exceptions.head
        }

        built
      }

    }

  }

}