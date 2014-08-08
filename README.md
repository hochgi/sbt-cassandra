sbt-cassandra-plugin
====================

This is a work in progress project.  The goal is to allow launching [Cassandra](http://cassandra.apache.org) during tests, and test your application against it (much like the [plugin for maven](http://mojo.codehaus.org/cassandra-maven-plugin)).
at this pre-mature phase, only the very basic functionality works (and only on linux/unix). API is not final, and might (probably will) change down the road.
However, the plugin is already usable as is.

## Installation ##
Add the following to your `project/plugins.sbt` file:
```scala
addSbtPlugin("com.github.hochgi" % "sbt-cassandra-plugin" % "0.1-SNAPSHOT")
```
Until i'll get this plugin hosted, you can build it yourself, and use `sbt publish-local` to have it available in your local `~/.ivy2`.

## Usage ##
### Basic: ###
```scala
import com.github.hochgi.sbt.cassandra._
    
seq(CassandraPlugin.cassandraSettings:_ *)
   
test in Test <<= (test in Test).dependsOn(startCassandra)
``` 
### Advanced: ##
To choose a specific version of cassandra (default is 2.0.6), you can use:
```scala
cassandraVersion := "2.0.6"
```
cassandra now shuts down & cleans the data by default when tests are done. to disable this behavior, set:
```scala
cleanCassandraAfterTests := false
```
and if you want cassandra to stay up:
```scala
stopCassandraAfterTests := false
```
to use special configuration files suited for your use case, use:
```scala
cassandraConfigDir := "/path/to/your/conf/dir"
```
to intialize cassandra with your custom cassandra-cli commands, use:
```scala
cassandraCliInit := "/path/to/cassandra-cli/commands/file"
```
to intialize cassandra with your custom cql commands, use:
```scala
cassandraCqlInit := "/path/to/cassandra-cql/commands/file"
```

to change cassandra rpc port (note: even if you change the port on the configuration, this is the port number that will be used), use:
```scala
cassandraPort := "PORT_NUMBER"
```

##### IMPORTANT NOTES #####
* don't use both CQL & CLI. choose only one...
* when overriding stop / clean, note that `cleanCassandraAfterTests` set to `true` while `stopCassandraAfterTests` set to `false` will be ignored.
