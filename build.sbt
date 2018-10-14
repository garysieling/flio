name := "flio"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies += "org.typelevel" %% "cats-effect" % "1.0.0"
libraryDependencies += "org.typelevel" %% "cats-effect-laws" % "1.0.0" % "test"
libraryDependencies += "org.typelevel" %% "discipline" % "0.10.0"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
libraryDependencies += "io.chrisdavenport" %% "fuuid" % "0.2.0-M1"

libraryDependencies += "com.lihaoyi" %% "ammonite-ops" % "1.2.1"


libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core"      % "0.5.3",
  "org.tpolecat" %% "doobie-postgres"  % "0.5.3", // Postgres driver 42.2.2 + type mappings.
)

// https://mvnrepository.com/artifact/org.postgresql/postgresql
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.5"

libraryDependencies ++= Seq(
  "io.chrisdavenport" %% "log4cats-slf4j"   % "0.2.0-M1",  // Direct Slf4j Support - Recommended
  //"io.chrisdavenport" %% "log4cats-log4s"   % "<version>",  // For Log4s Support
  //"io.chrisdavenport" %% "log4cats-scribe"  % "<version>",   // For Scribe Support
  //"io.chrisdavenport" %% "log4scalaz-log4s" % "<version>"   // Scalaz Log4s Support
)

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % "0.10.0")
