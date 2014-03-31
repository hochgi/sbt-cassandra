package com.github.hochgi.sbt.cassandra

import sbt._
import Keys._

object CassandraPlugin extends Plugin {
	
	//defaults:
	private[this] val defaultConfigDir = "NO_DIR_SUPPLIED"
	private[this] val defaultCliInit = "NO_CLI_COMMANDS_SUPPLIED"
	private[this] val defaultCqlInit = "NO_CQL_COMMANDS_SUPPLIED"

	val cassandraVersion = SettingKey[String]("cassandra-version")
	val cassandraConfigDir = SettingKey[String]("cassandra-config-dir")
	val cassandraCliInit = SettingKey[String]("cassandra-cli-init")
	val cassandraCqlInit = SettingKey[String]("cassandra-cql-init")
	val cassandraJvmArgs = TaskKey[Seq[String]]("cassandra-jvm-args")
	val deployCassandra = TaskKey[File]("deploy-cassandra")
	val startCassandra = TaskKey[File]("start-cassandra")
	val cassandraPid = TaskKey[String]("cassandra-pid")
	
	val cassandraSettings = Seq(
		cassandraConfigDir := defaultConfigDir,
		cassandraCliInit := defaultCliInit,
		cassandraCqlInit := defaultCqlInit,
		cassandraJvmArgs := Seq(),
		cassandraJvmArgs <++= (cassandraVersion, target in Test) map {
			case (ver, targetDir) => { 
				val jamm: File = targetDir / s"apache-cassandra-${ver}" / "lib" / "jamm-0.2.5.jar"
				Seq("-server",
					"-ea",
					s"-javaagent:${jamm.getAbsolutePath}",
					"-Xms1984M",
					"-Xmx1984M",
					"-Xmn400M",
					"-XX:+HeapDumpOnOutOfMemoryError",
					"-Xss256k",
					"-XX:StringTableSize=1000003",
					"-XX:+UseParNewGC",
					"-XX:+UseConcMarkSweepGC",
					"-XX:+CMSParallelRemarkEnabled",
					"-XX:SurvivorRatio=8",
					"-XX:MaxTenuringThreshold=1",
					"-XX:CMSInitiatingOccupancyFraction=75",
					"-XX:+UseCMSInitiatingOccupancyOnly",
					"-XX:+UseTLAB",
					"-XX:+UseCondCardMark",
					"-Djava.net.preferIPv4Stack=true",
					"-Dcom.sun.management.jmxremote.port=7199",
					"-Dcom.sun.management.jmxremote.ssl=false",
					"-Dcom.sun.management.jmxremote.authenticate=false",
					"-Dlog4j.configuration=log4j-server.properties",
					"-Dlog4j.defaultInitOverride=true")
			}
		},
		cassandraVersion := "2.0.6",
		classpathTypes ~=  (_ + "tar.gz"),
		libraryDependencies += {
			"org.apache.cassandra" % "apache-cassandra" % cassandraVersion.value artifacts(Artifact("apache-cassandra", "tar.gz", "tar.gz","bin")) intransitive()
		},
		deployCassandra <<= (cassandraVersion, target in Test, dependencyClasspath in Runtime) map {
			case (ver, targetDir, classpath) => {
				val cassandraTarGz = Build.data(classpath).find(_.getName.endsWith(".tar.gz")).get
				if (cassandraTarGz == null) sys.error("could not load: cassandra tar.gz file.")
				println(s"cassandraTarGz: ${cassandraTarGz.getAbsolutePath}")
				Process(Seq("tar","-xzf",cassandraTarGz.getAbsolutePath),targetDir).!
				targetDir / s"apache-cassandra-${ver}"
			}
		},
		startCassandra <<= (target, deployCassandra, cassandraJvmArgs, cassandraConfigDir,cassandraCliInit,cassandraCqlInit) map {
			case (targetDir, cassHome, jvmOpts, confDirAsString, cli, cql) => {
				val pidFile = targetDir / "cass.pid"
				val jarClasspath = sbt.IO.listFiles(cassHome / "lib").collect{case f: File if f.getName.endsWith(".jar") => f.getAbsolutePath}.mkString(":")
				val classpath = (cassHome / "conf").getAbsolutePath + ":" + jarClasspath
				
				val conf = {
					if(confDirAsString == defaultConfigDir) {
						val configDir = targetDir / "cass.conf"
						if(!(configDir.exists && configDir.isDirectory)) makeConfigDirectory(configDir)
						println("configDir:" + configDir.getAbsolutePath)
						configDir.getAbsolutePath
					} else confDirAsString
				}
				val pid = s"-Dcassandra-pidfile=${pidFile.getAbsolutePath}"
				println("[INFO] going to run cassandra:")
				Process(Seq("java",pid) ++ jvmOpts ++ Seq("-cp",s"${conf}:${classpath}","org.apache.cassandra.service.CassandraDaemon"),cassHome, "CASSANDRA_CONF" -> conf).run
				println("[INFO] going to wait for cassandra:")
				waitForCassandra
				println("[INFO] going to initialize cassandra:")
				initCassandra(cli,cql,classpath,cassHome)
				pidFile
			}
		},
		cassandraPid <<= (target) map {
			t => {
				val cassPid = t / "cass.pid"
				if(cassPid.exists) sbt.IO.read(cassPid).filterNot(_.isWhitespace)
				else "NO-PID"
			}
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
	def waitForCassandra: Unit = {
		import org.apache.thrift.transport.{TTransport, TFramedTransport, TSocket, TTransportException}

		val rpcAddress = "localhost"
		val rpcPort = 9160
		var continue = false
		while (!continue) {
			continue = true
			val tr: TTransport = new TFramedTransport(new TSocket(rpcAddress, rpcPort))
			try {
				tr.open;
			} catch {
				case e: TTransportException => {
					println("[INFO] waiting for cassandra to boot")
					Thread.sleep(500)
					continue = false
				}
			}
			if (tr.isOpen) tr.close
		}
	}
	def initCassandra(cli: String, cql: String, classpath: String, cassHome: File): Unit = {
		if(cli != defaultCliInit && cql != defaultCqlInit) sys.error("use cli initiation commands, or cql initiation commands, but not both!")
		else if(cli != defaultCliInit) {
			Process(Seq("java","-ea","-cp",classpath,"-Xmx256M","-Dlog4j.configuration=log4j-tools.properties","org.apache.cassandra.cli.CliMain","-f",cli),
					cassHome,"CASSANDRA_HOME" -> cassHome.getAbsolutePath).!
		}
		else if(cql != defaultCqlInit) {
			//TODO: use cql commands...
		}
	}
}
