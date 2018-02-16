sbtPlugin := true

organization := "com.github.hochgi"

name := "sbt-cassandra"

description := "SBT plugin to launch and use Cassandra during integration tests"

version := "1.0.4"

scalaVersion := "2.10.6"

libraryDependencies ++= Seq("org.apache.thrift" % "libthrift" % "0.9.2" exclude("commons-logging","commons-logging"),
                            "org.slf4j" % "slf4j-api" % "1.7.12",
                            "org.slf4j" % "jcl-over-slf4j" % "1.7.12",
                            "org.yaml" % "snakeyaml" % "1.15",
                            "me.lessis" %% "semverfi" % "0.1.3")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:postfixOps")

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                  Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

publishArtifact in (Compile, packageSrc) :=  true

pomIncludeRepository := { x => false }

homepage := Some(url("http://github.com/hochgi/sbt-cassandra-plugin"))

licenses := Seq("The MIT License (MIT)" -> url("http://opensource.org/licenses/MIT"))

pomExtra := (
  <scm>
    <url>https://github.com/hochgi/sbt-cassandra-plugin.git</url>
    <connection>scm:git@github.com:hochgi/sbt-cassandra-plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>hochgi</id>
      <name>Gilad Hoch</name>
      <url>http://hochgi.blogspot.com</url>
    </developer>
    <developer>
      <id>milliondreams</id>
      <name>Rohit Rai</name>
      <url>http://www.twitter.com/milliondreams</url>
    </developer>
    <developer>
      <id>Shiti</id>
      <name>Shiti Saxena</name>
      <url>https://twitter.com/eraoferrors</url>
    </developer>
  </developers>
)
