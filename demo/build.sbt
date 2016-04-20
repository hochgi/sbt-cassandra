name := "demo"

version := "1.0.0"

scalaVersion := "2.11.8"

lazy val root = (project in file("."))
  .enablePlugins(CassandraITPlugin)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(cassandraVersion := "3.4")

