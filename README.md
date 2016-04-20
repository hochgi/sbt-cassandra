sbt-cassandra
==============

An auto-plugin that launches [Cassandra](http://cassandra.apache.org) during integration tests.
Note: The plugin does not work on WindowsOS

## Installation ##
Add the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("com.tuplejump.com.github.hochgi" % "sbt-cassandra"   % "1.0.4")
```

## Usage ##
### Basic: ###
In `build.sbt`, enable the plugin for desired project and specify the version of cassandra against which tests are to be run, 

```scala
lazy val root = (project in file("."))
  .enablePlugins(CassandraITPlugin)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(cassandraVersion := "3.4")
```

### Additional Settings: ##

cassandra now shuts down by default when tests are done. to disable this behavior, set:
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
to intialize cassandra with your custom cql commands, use:
```scala
cassandraCqlInit := "/path/to/cassandra-cql/commands/file"
```
timeout for waiting on cassandra to start (default is 20 seconds) can be configured with property (in seconds):
```scala
cassandraStartDeadline := 10
```
the plugin downloads cassandra tar from [Apache Cassandra Archives](http://archive.apache.org/dist/cassandra/). A custom version can be used by configuring, the path to the tar file,
```scala
cassandraTgz := "resources/custom-cassandra.tgz"
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
Note: the `configMappings` key takes a sequence of `(String,java.lang.Object)`, and should be compatible with actual value represented by the key in the yaml file.

The `configMappings` setting should be used to change host, port and cqlPort. The keys for them are:

* host - `listen_address`
* port - `rpc_port`
* cqlPort - `native_transport_port`
        
to pass java args to cassandra,
```scala
cassandraJavaArgs := Seq("-Xmx1G")
```
        
