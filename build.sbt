sbtPlugin := true

organization := "com.github.hochgi"

name := "sbt-cassandra-plugin"

version := "0.4-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq("org.apache.thrift" % "libthrift" % "0.9.2",
                            "org.slf4j" % "slf4j-api" % "1.7.7",
                            "org.slf4j" % "jcl-over-slf4j" % "1.7.7",
                            "org.yaml" % "snakeyaml" % "1.14",
                            "me.lessis" %% "semverfi" % "0.1.3")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:postfixOps")

externalDependencyClasspath in Compile ~= (_.filterNot(_.data.toString.contains("commons-logging")))