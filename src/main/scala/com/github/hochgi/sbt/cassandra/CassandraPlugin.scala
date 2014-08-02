package com.github.hochgi.sbt.cassandra

import sbt._
import Keys._
import scala.concurrent._ ,duration._
import ExecutionContext.Implicits.global
import scala.io.Source
import scala.reflect.io.{File => RFile}
import org.yaml.snakeyaml.Yaml
import java.io.FileInputStream

object CassandraPlugin extends Plugin {
	
	//defaults:
	private[this] val defaultConfigDir = "NO_DIR_SUPPLIED"
	private[this] val defaultCliInit = "NO_CLI_COMMANDS_SUPPLIED"
	private[this] val defaultCqlInit = "NO_CQL_COMMANDS_SUPPLIED"

	val cassandraVersion = SettingKey[String]("cassandra-version")
	val cassandraConfigDir = SettingKey[String]("cassandra-config-dir")
	val cassandraCliInit = SettingKey[String]("cassandra-cli-init")
	val cassandraCqlInit = SettingKey[String]("cassandra-cql-init")
	val cassandraHost = SettingKey[String]("cassandra-host")
	val cassandraPort = SettingKey[String]("cassandra-port")
	val cassandraHome = TaskKey[File]("cassandra-home")
	val deployCassandra = TaskKey[File]("deploy-cassandra")
	val startCassandra = TaskKey[sbt.Def.Setting[sbt.Task[String]]]("start-cassandra")
	val stopCassandraAfterTests = SettingKey[Boolean]("stop-cassandra-after-tests")
	val cleanCassandraAfterTests = SettingKey[Boolean]("clean-cassandra-after-tests")
	val cassandraPid = TaskKey[String]("cassandra-pid")
        val stopCassandra = TaskKey[Int]("stop-cassandra")
	
	val cassandraSettings = Seq(
        cassandraHost := "localhost",
        cassandraPort := "9160",
		cassandraConfigDir := defaultConfigDir,
		cassandraCliInit := defaultCliInit,
		cassandraCqlInit := defaultCqlInit,
		stopCassandraAfterTests := true,
		cleanCassandraAfterTests := true,
		cassandraHome <<= (cassandraVersion, target) map {case (ver,targetDir) => targetDir / s"apache-cassandra-${ver}"},
		cassandraVersion := "2.0.6",
		classpathTypes ~=  (_ + "tar.gz"),
		libraryDependencies += {
			"org.apache.cassandra" % "apache-cassandra" % cassandraVersion.value artifacts(Artifact("apache-cassandra", "tar.gz", "tar.gz","bin")) intransitive()
		},
		deployCassandra <<= (cassandraVersion, target, dependencyClasspath in Runtime) map {
			case (ver, targetDir, classpath) => {
				val cassandraTarGz = Attributed.data(classpath).find(_.getName == s"apache-cassandra-$ver-bin.tar.gz").get
				if (cassandraTarGz == null) sys.error("could not load: cassandra tar.gz file.")
				println(s"cassandraTarGz: ${cassandraTarGz.getAbsolutePath}")
				Process(Seq("tar","-xzf",cassandraTarGz.getAbsolutePath),targetDir).!
				targetDir / s"apache-cassandra-${ver}"
			}
		},
		startCassandra <<= (target, deployCassandra, cassandraConfigDir,cassandraCliInit,cassandraCqlInit,cassandraHost,cassandraPort) map {
			case (targetDir, cassHome, confDirAsString, cli, cql, host, port) => {
				val pidFile = targetDir / "cass.pid"
				val jarClasspath = sbt.IO.listFiles(cassHome / "lib").collect{case f: File if f.getName.endsWith(".jar") => f.getAbsolutePath}.mkString(":")
				
				val conf: Types.Id[String] = {
					if(confDirAsString == defaultConfigDir) {
						val configDir = targetDir / "cass.conf"
						if(!(configDir.exists && configDir.isDirectory)) makeConfigDirectory(configDir)
						println("configDir:" + configDir.getAbsolutePath)
						configDir.getAbsolutePath
					} else confDirAsString
				}
				val classpath = conf + ":" + jarClasspath
				val bin = cassHome / "bin" / "cassandra"
				val args = Seq(bin.getAbsolutePath, "-p", pidFile.getAbsolutePath)
        if (port != "9160")
          setCassandraRpcPort(conf, port)
				Process(args,cassHome, "CASSANDRA_CONF" -> conf).run
				println("[INFO] going to wait for cassandra:")
				waitForCassandra(port)
				println("[INFO] going to initialize cassandra:")
				initCassandra(cli,cql,classpath,cassHome,host,port)
				cassandraPid := sbt.IO.read(pidFile).filterNot(_.isWhitespace)
			}
		},
		//if compilation of test classes fails, cassandra should not be invoked. (moreover, Test.Cleanup won't execute to stop it...)
		startCassandra <<= (startCassandra).dependsOn(compile in Test),
		cassandraPid <<= (target) map {
			t => {
				val cassPid = t / "cass.pid"
				if(cassPid.exists) sbt.IO.read(cassPid).filterNot(_.isWhitespace)
				else "NO PID" // did you run start-cassandra task?
			}
		},
		stopCassandra <<= (cassandraPid) map {
      case (pid) => s"kill $pid" !
    },
		//make sure to Stop cassandra when tests are done.
		testOptions in Test <+= (cassandraPid, cassandraHome, stopCassandraAfterTests, cleanCassandraAfterTests) map {
			case (pid, home, stop, clean) => Tests.Cleanup(() => {
				if(stop && pid != "NO PID") {
					s"kill $pid" !
					//give cassandra a chance to exit gracefully
					var counter = 40
					val never = Promise[Boolean].future
					while((s"jps" !!).split("\n").exists(_ == s"$pid CassandraDaemon") && counter > 0) {
						try{
							Await.ready(never, 250 millis)
						} catch {
							case _ : Throwable => counter = counter - 1
						}
					}
					if(counter == 0) {
						//waited to long...
						s"kill -9 $pid" !
					}
					if(clean) {
						val cassData = (home / "data").getAbsolutePath
						s"rm -r $cassData" !
					}
				}
			})
		}
	)
	
	def makeConfigDirectory(configDir: File) = {
		if(configDir.exists) {
			if(!configDir.isDirectory) {sys.error("could not create conf dir (file in that name exists. use clean to start from scratch).")}
		} else {
			configDir.mkdirs		
			val ccz = getClass.getClassLoader.getResourceAsStream("cass.conf.zip")
			if (ccz == null) sys.error("could not load: cass.conf.zip")
			sbt.IO.unzipStream(ccz, configDir)
		}
	}
	def waitForCassandra(port: String): Unit = {
		import org.apache.thrift.transport.{TTransport, TFramedTransport, TSocket, TTransportException}

		val rpcAddress = "localhost"
		val rpcPort = port.toInt
		var continue = false
		while (!continue) {
			continue = true
			val tr: TTransport = new TFramedTransport(new TSocket(rpcAddress, rpcPort))
			try {
				tr.open;
			} catch {
				case e: TTransportException => {
					println(s"[INFO] waiting for cassandra to boot on port $rpcPort")
					Thread.sleep(500)
					continue = false
				}
			}
			if (tr.isOpen) tr.close
		}
	}
	def initCassandra(cli: String, cql: String, classpath: String, cassHome: File, host: String, port: String): Unit = {
		if(cli != defaultCliInit && cql != defaultCqlInit) sys.error("use cli initiation commands, or cql initiation commands, but not both!")
		else if(cli != defaultCliInit) {
			val bin = cassHome / "bin" / "cassandra-cli"
			val args = Seq(bin.getAbsolutePath, "-f", cli,"-h",host,"-p",port)
			Process(args,cassHome).!
		}
		else if(cql != defaultCqlInit) {
			val bin = cassHome / "bin" / "cqlsh"
			val args = Seq(bin.getAbsolutePath, "-f", cql,host,port)
			Process(args,cassHome).!
		}
	}

  def setCassandraRpcPort(conf: Types.Id[String], port:  String): Unit = {
    println(s"[INFO] setting new rpc port for cassandra: port $port")
    val cassandraYamlPath = s"$conf/cassandra.yaml"
    val yaml = new Yaml
    var cassandraYamlMap = yaml.load(new FileInputStream(new File(cassandraYamlPath)))
                           .asInstanceOf[java.util.LinkedHashMap[String, String]]
    cassandraYamlMap.put("rpc_port", port)
    val newCassandraYamlFileContent = yaml.dump(cassandraYamlMap)
    RFile(cassandraYamlPath).writeAll(newCassandraYamlFileContent)
  }
}
