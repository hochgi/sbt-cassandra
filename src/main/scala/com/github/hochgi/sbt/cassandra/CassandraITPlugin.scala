package com.github.hochgi.sbt.cassandra

import java.io.{FileInputStream, FileOutputStream}
import java.util.{Properties, Map => JMap}

import sbt._
import Keys._
import org.yaml.snakeyaml.Yaml

import scala.concurrent.{Await, Promise}
import scala.util.Try
import scala.collection.JavaConverters._
import scala.concurrent.duration._

object CassandraITPlugin extends AutoPlugin {

  object autoImport {
    lazy val cassandraVersion = SettingKey[String]("cassandra-version",
      "Required. Version of Cassandra to be used for testing.")
    lazy val cassandraConfigDir = SettingKey[String]("cassandra-config-dir")

    lazy val configMappings = SettingKey[Seq[(String, java.lang.Object)]]("cassandra-conf",
      "Optional. used to override values in conf/cassandra.yaml. values are appropriate java objects")
    lazy val cassandraCqlInit = SettingKey[String]("cassandra-cql-init",
      "Optional. cql script to be executed before starting tests. Useful for loading test data.")

    lazy val cassandraTgz = SettingKey[String]("cassandra-tgz",
      "Optional. Path to Cassandra binary tar file")

    lazy val cassandraStartDeadline = SettingKey[Int]("cassandra-start-deadline")
    lazy val stopCassandraAfterTests = SettingKey[Boolean]("stop-cassandra-after-tests",
      "Optional. Defaults to true")
    lazy val cleanCassandraAfterStop = SettingKey[Boolean]("clean-cassandra-after-stop",
      "Optional. Defaults to true")

    lazy val cassandraJavaArgs = SettingKey[Seq[String]]("cassandra-java-args",
      "Optional. Java arguments to be passed to Cassandra")

  }

  private val PIDFileName = "cass.pid"

  def deployCassandra(tarFile: String,
                      casVersion: String,
                      targetDir: File,
                      logger: Logger): File = {
    val cassandraTarGz: File = if (tarFile.nonEmpty) {
      file(tarFile)
    } else if (casVersion.nonEmpty) {
      val file: File = new File(targetDir, s"apache-cassandra-$casVersion-bin.tar.gz")
      val source = s"http://archive.apache.org/dist/cassandra/$casVersion/apache-cassandra-$casVersion-bin.tar.gz"
      IO.download(url(source), file)
      file
    } else {
      sys.error("Specify Cassandra version or path to Cassandra tar.gz file")
    }

    if (cassandraTarGz == null) sys.error("could not load: cassandra tar.gz file.")
    logger.info(s"cassandraTarGz: ${cassandraTarGz.getAbsolutePath}")
    val fileName: String = cassandraTarGz.getName
    val extensionIndex = fileName.indexOf(".tar.gz")
    val dirName = fileName.substring(0, extensionIndex)
    Process(Seq("tar", "-xzf", cassandraTarGz.getAbsolutePath), targetDir).!
    val cassHome = targetDir / dirName
    //old cassandra versions used log4j, newer versions use logback and are configurable through env vars
    val oldLogging = cassHome / "conf" / "log4j-server.properties"
    if (oldLogging.exists) {
      val in: FileInputStream = new FileInputStream(oldLogging)
      val props: Properties = new Properties
      props.load(in)
      in.close()
      val out: FileOutputStream = new FileOutputStream(oldLogging)
      props.setProperty("log4j.appender.R.File", (targetDir / "data" / "logs").getAbsolutePath)
      props.store(out, null)
      out.close()
    }
    cassHome
  }

  def initCassandra(cql: String,
                    cassHome: File,
                    host: String,
                    cqlPort: Int): Unit = {
    if (cql.nonEmpty) {
      val bin = cassHome / "bin" / "cqlsh"
      val cqlPath = new File(cql).getAbsolutePath
      val args = Seq(bin.getAbsolutePath, "-f", cqlPath, host, cqlPort.toString)
      Process(args, cassHome).!
    }
  }

  def isCassandraRunning(rpcAddress: String,
                         rpcPort: Int): Boolean = {
    import org.apache.thrift.transport.{TFramedTransport, TSocket}
    val tr = new TFramedTransport(new TSocket(rpcAddress, rpcPort))
    Try {
      tr.open()
    }.isSuccess
  }

  def waitForCassandra(rpcAddress: String,
                       rpcPort: Int,
                       deadline: Int,
                       infoPrintFunc: String => Unit): Unit = {
    import org.apache.thrift.transport.{TFramedTransport, TSocket, TTransport, TTransportException}

    import scala.concurrent.duration._

    var retry = true
    val deadlineTime = deadline.seconds.fromNow
    while (retry && deadlineTime.hasTimeLeft) {
      val tr: TTransport = new TFramedTransport(new TSocket(rpcAddress, rpcPort))
      try {
        tr.open()
        retry = false
      } catch {
        case e: TTransportException => {
          infoPrintFunc(s"waiting for cassandra to boot on port $rpcPort")
          Thread.sleep(500)
        }
      }
      if (tr.isOpen) {
        tr.close()
      }
    }
  }

  def updateCassandraConfig(confDir: String,
                            confMappings: Seq[(String, java.lang.Object)],
                            logger: Logger): JMap[String, Object] = {
    val filePath: String = s"$confDir/cassandra.yaml"
    val yamlFile = new FileInputStream(new File(filePath))
    val yaml = new Yaml
    val cassandraYamlMap = (yaml.load(yamlFile).asInstanceOf[JMap[String, Object]].asScala ++ confMappings.toMap).asJava

    val ymlContent = yaml.dump(cassandraYamlMap)
    logger.debug(ymlContent)
    sbt.IO.write(file(filePath), ymlContent, java.nio.charset.StandardCharsets.UTF_8, false)

    cassandraYamlMap
  }

  def startCassandra(cassHome: File,
                     host: String,
                     port: Int,
                     targetDir: File,
                     conf: String,
                     logger: Logger,
                     javaArgs: Seq[String],
                     startDeadline: Int): Unit = {

    val pidFile = targetDir / PIDFileName

    if (!isCassandraRunning(host, port)) {
      val bin = cassHome / "bin" / "cassandra"
      val args = Seq(bin.getAbsolutePath, "-p", pidFile.getAbsolutePath)
      Process(args, cassHome, "CASSANDRA_CONF" -> conf,
        "CASSANDRA_HOME" -> cassHome.getAbsolutePath, "JVM_OPTS" -> javaArgs.mkString(" ")).run
      logger.info("going to wait for cassandra:")
      waitForCassandra(host, port, startDeadline, (s: String) => logger.info(s))
    } else {
      logger.warn("cassandra already running")
    }

  }

  def stopCassandra(clean: Boolean,
                    dataDir: File,
                    pid: String) = {
    if (pid.nonEmpty) {
      s"kill $pid" !
      //give cassandra a chance to exit gracefully
      var counter = 40
      val never = Promise().future
      while ((s"jps" !!).split("\n").contains(s"$pid CassandraDaemon") && counter > 0) {
        try {
          Await.ready(never, 250 millis)
        } catch {
          case _: Throwable => counter = counter - 1
        }
      }
      if (counter == 0) {
        //waited too long...
        s"kill -9 $pid" !
      }
      if (clean) {
        sbt.IO.delete(dataDir)
      }
    }
  }

  import autoImport._

  lazy val cassandraITDefaultSettings: Seq[Def.Setting[_]] = Seq(
    cassandraVersion := "",
    cassandraConfigDir := "",
    cassandraCqlInit := "",
    cassandraTgz := "",
    stopCassandraAfterTests := true,
    cleanCassandraAfterStop := true,
    cassandraStartDeadline := 20,
    configMappings := Nil,
    cassandraJavaArgs := Nil
  )

  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] = super.projectSettings ++
    cassandraITDefaultSettings ++ Seq(
    fork in IntegrationTest := true,
    parallelExecution in IntegrationTest := false,
    testOptions in IntegrationTest += Tests.Setup { () =>
      val targetDir = target.value
      val logger = streams.value.log

      val cassHome = deployCassandra(cassandraTgz.value.trim,
        cassandraVersion.value.trim, targetDir, logger)

      val confDir: String = {
        if (cassandraConfigDir.value.trim.isEmpty) {
          (cassHome / "conf").getAbsolutePath
        } else cassandraConfigDir.value.trim
      }

      val updatedConfig = updateCassandraConfig(confDir, configMappings.value, logger)
      val host = updatedConfig.get("listen_address").asInstanceOf[String]
      val port = updatedConfig.get("rpc_port").asInstanceOf[Int]
      val cqlPort = updatedConfig.get("native_transport_port").asInstanceOf[Int]

      startCassandra(cassHome, host, port, targetDir, confDir,
        logger, cassandraJavaArgs.value, cassandraStartDeadline.value)

      logger.info("going to initialize cassandra:")
      initCassandra(cassandraCqlInit.value.trim, cassHome, host, cqlPort)
    },

    testOptions in IntegrationTest += Tests.Cleanup { () =>
      if (stopCassandraAfterTests.value) {
        val targetDir = target.value
        val pidFile = targetDir / PIDFileName
        val pid = Try(sbt.IO.read(pidFile).filterNot(_.isWhitespace)).getOrElse("")
        stopCassandra(cleanCassandraAfterStop.value, targetDir / "data", pid)
      }
    }
  )
}
