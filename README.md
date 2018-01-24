# sbt-pillar-plugin - manage Cassandra migrations from sbt

[![Join the chat at https://gitter.im/inoio/sbt-pillar-plugin](https://badges.gitter.im/inoio/sbt-pillar-plugin.svg)](https://gitter.im/inoio/sbt-pillar-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/inoio/sbt-pillar-plugin.png?branch=master)](https://travis-ci.org/inoio/sbt-pillar-plugin)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.ino/sbt-pillar-plugin/badge.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.ino%22%20a%3A%22sbt-pillar-plugin%22)

This sbt plugin allows to run Cassandra schema/data migrations from sbt (using [pillar](https://github.com/Galeria-Kaufhof/pillar)).
 For details on migration files check out the [pillar documentation](https://github.com/Galeria-Kaufhof/pillar#migration-files).
 The cassandra connection configuration is not based on pillar but we're using our own format (see [Configuration](#configuration)).

The plugin is built for sbt 1.0 and 0.13.

## Installation

To install the plugin you have to add it to `project/plugins.sbt`:
```
addSbtPlugin("io.ino" %% "sbt-pillar-plugin" % "2.1.3")
```

## Configuration

Add appropriate configuration to `build.sbt` like this:
```
import io.ino.sbtpillar.Plugin.PillarKeys._

...

pillarSettings

pillarConfigFile := file("conf/application.conf")

pillarConfigKey := "cassandra.url"

pillarReplicationStrategyConfigKey := "cassandra.replicationStrategy"

pillarReplicationFactorConfigKey := "cassandra.replicationFactor"

pillarDefaultConsistencyLevelConfigKey := "cassandra.defaultConsistencyLevel"

pillarReplicationStrategyConfigKey := "cassandra.replicationStrategy"

pillarDatacenterNamesConfigKey := "cassandra.datacenterNames"

pillarMigrationsDir := file("conf/migrations")

//optionally:
pillarExtraMigrationsDirs := Seq(file("conf/extra-migrations"))
```

The shown configuration assumes that the url for your cassandra is configured in `conf/application.conf` under the key
`cassandra.url` and that pillar migration files are kept in `conf/migrations` (regarding the format of migration files
check out the [pillar documentation](https://github.com/Galeria-Kaufhof/pillar#migration-files)).

The `cassandra.url` has to follow the format `cassandra://<host>:<port>/<keyspace>?host=<host>&host=<host>`, e.g. it would be
`cassandra.url="cassandra://192.168.0.10:9042/my_keyspace?host=192.168.0.11&host=192.168.0.12"`.

The `pillarMigrationsDir` contains the directory with the cql-files to process. It's processed recursively, therefore files in subfolders are picked up as well.

The `pillarReplicationStrategyConfigKey` is optional, the default value is `SimpleStrategy`. `NetworkTopologyStrategy` is also supported.

The `pillarDatacenterNamesConfigKey` is only used if the `NetworkTopologyStrategy` is chosen. You can place your datecenter names here in a list `["eu", "na", "sa"]`. This will result in the following replication class:  `replication = {'class': 'NetworkTopologyStrategy', 'eu': '3', 'na': '3', 'sa': '3'}`

The `pillarReplicationFactorConfigKey` is optional, the default value is `3`.

The `pillarDefaultConsistencyLevelConfigKey` is optional, the default value is `QUORUM`.

The `pillarExtraMigrationsDirs` is optional, the default is `Seq.empty`.  Here you can add directories containing extra migration files, which will be processed in addition to the ones in `pillarMigrationsDir`.

## Usage

The sbt pillar plugin provides the following tasks:

<dl>
<dt>createKeyspace</dt><dd>Creates the keyspace (and creates pillar's <code>applied_migrations</code> table)</dd>
<dt>dropKeyspace</dt><dd>Drops the keyspace</dd>
<dt>migrate</dt><dd>Runs pillar migrations (assumes <code>createKeyspace</code> was run before)</dd>
<dt>cleanMigrate</dt><dd>Recreates the keyspace (drops if exists && creates) and runs pillar migrations (useful for continuous integration scenarios)</dd>
</dl>

## License

The license is Apache 2.0, see LICENSE.txt.
