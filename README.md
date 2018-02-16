sbt-cassandra
==============

An auto-plugin that launches [Cassandra](http://cassandra.apache.org).
at this pre-mature phase, only the very basic functionality works (and only on linux/unix). API is not final, and might (probably will) change down the road.
However, the plugin is already usable as is.

Note: The plugin does not work on WindowsOS

## Installation ##
Add the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("com.github.hochgi" % "sbt-cassandra"   % "0.9.0")
```

## Usage ##
### Basic: ###
In `build.sbt`, enable the plugin for desired project and specify the version of cassandra against which tests are to be run,

```scala
lazy val root = (project in file("."))
  .enablePlugins(CassandraPlugin)
  .settings(Defaults.itSettings: _*)
  .settings(cassandraVersion := "2.1.19")
```

### Additional Settings: ##

Cassandra now shuts down by default when tests are done. to disable this behavior, set:
```scala
stopCassandraAfterTests := false
```
cassandra will also clean it's data by default when it stops (after tests or when invoking `stopCassandra` task explicitly). to disable this behavior, set:
```scala
cleanCassandraAfterStop := false
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
timeout for waiting on cassandra to start (default is 20 seconds) can be configured with property (in seconds):
```scala
cassandraStartDeadline := 10
```
to override cassandra configuration, e.g:
```scala
configMappings +=  "auto_snapshot" -> true
configMappings ++= Seq(
  "rpc_server_type" -> "sync",
  "data_file_directories" -> {
    val list = new java.util.LinkedList[String]()
    list.add("/path/to/directory/on/disk1")
    list.add("/path/to/directory/on/disk2")
    list.add("/path/to/directory/on/disk3")
    list
  }
)
```
##### IMPORTANT NOTES #####
* don't use both CQL & CLI. choose only one...
* the `configMappings` key takes a sequence of `(String,java.lang.Object)`, and should be compatible with actual value represented by the key in the yaml file. The `configMappings` setting should be used to change host, port and cqlPort. The keys for them are:

* host - `listen_address`
* port - `rpc_port`
* cqlPort - `native_transport_port`

to pass java args to cassandra,
```scala
cassandraJavaArgs := Seq("-Xmx1G")
```
