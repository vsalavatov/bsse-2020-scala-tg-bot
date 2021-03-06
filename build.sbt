name := "aipisibot"

version := "0.1"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "com.bot4s" %% "telegram-core" % "4.4.0-RC2",
  "com.bot4s" %% "telegram-akka" % "4.4.0-RC2",
  "com.softwaremill.sttp" %% "json4s" % "1.7.2",
  "org.json4s" %% "json4s-native" % "3.6.0",
  "com.github.scopt" % "scopt_2.12" % "4.0.0-RC2" // arguments parser
)