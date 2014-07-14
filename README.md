# sbt-pillar-plugin - manage Cassandra migrations from sbt

[![Build Status](https://travis-ci.org/inoio/sbt-pillar-plugin.png?branch=master)](https://travis-ci.org/inoio/sbt-pillar-plugin)

This sbt plugin allows to run Cassandra schema/data migrations from sbt (using [pillar](https://github.com/comeara/pillar)).
 For details on migration files check out the [pillar documentation](https://github.com/comeara/pillar#migration-files).
 The cassandra connection configuration is not based on pillar but we're using our own format (see [Configuration](#configuration)).

The plugin is built for sbt 0.13.

## Installation

To install the plugin you have to add it to `project/plugins.sbt`:
```
addSbtPlugin("io.ino" %% "sbt-pillar-plugin" % "1.0.0")
```

## Configuration

Add appropriate configuration to `build.sbt` like this:
```
import io.ino.sbtpillar.Plugin.PillarKeys._

...

pillarSettings

pillarConfigFile := file("conf/application.conf")

pillarConfigKey := "cassandra.url"

pillarReplicationFactorConfigKey := "cassandra.replicationFactor"

pillarMigrationsDir := file("conf/migrations")
```

The shown configuration assumes that the url for your cassandra is configured in `conf/application.conf` under the key
`cassandra.url` and that pillar migration files are kept in `conf/migrations` (regarding the format of migration files
check out the [pillar documentation](https://github.com/comeara/pillar#migration-files)).

The `cassandra.url` has to follow the format `cassandra://<host>:<port>/<keyspace>?host=<host>&host=<host>`, e.g. it would be
`cassandra.url="cassandra://192.168.0.10:9042/my_keyspace?host=192.168.0.11&host=192.168.0.12"`.

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
