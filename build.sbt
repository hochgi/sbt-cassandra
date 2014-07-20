sbtPlugin := true

organization := "com.github.hochgi"

name := "sbt-cassandra-plugin"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

libraryDependencies ++= Seq("org.apache.thrift" % "libthrift" % "0.9.1",
                            "org.slf4j" % "slf4j-api" % "1.7.6")


scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:postfixOps")


