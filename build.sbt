sbtPlugin := true

organization := "com.github.hochgi"

name := "sbt-cassandra-plugin"

description := "SBT plugin to allow launching Cassandra during tests, and test your application against it"

version := "0.5"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq("org.apache.thrift" % "libthrift" % "0.9.2",
                            "org.slf4j" % "slf4j-api" % "1.7.7",
                            "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
                            "org.yaml" % "snakeyaml" % "1.14",
                            "me.lessis" %% "semverfi" % "0.1.3")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:postfixOps")

externalDependencyClasspath in Compile ~= (_.filterNot(_.data.toString.contains("commons-logging")))


publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                             Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

pomExtra := (
  <url>http://github.com/hochgi/sbt-cassandra-plugin</url>
  <licenses>
    <license>
      <name>The MIT License (MIT)</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:kpbochenek/sbt-cassandra-plugin.git</url>
    <connection>scm:git:git@github.com:kpbochenek/sbt-cassandra-plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>hochgi</id>
      <name>Thomson Reuters</name>
      <url>https://github.com/hochgi</url>
      </developer>
  </developers>
)
