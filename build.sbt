name := "gitbucket-backup-plugin"
organization := "io.github.gitbucket"
version := "1.7.0-SNAPSHOT"
scalaVersion := "2.13.18"
gitbucketVersion := "4.48.0"

libraryDependencies ++= Seq(
  "org.zeroturnaround" % "zt-zip" % "1.15",
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.1034",
  "org.quartz-scheduler" % "quartz" % "2.5.2" exclude("jakarta.xml.bind", "jakarta.xml.bind-api")
)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test"

scalacOptions ++= Seq("-deprecation", "-feature")

// Tests run against a throwaway GITBUCKET_HOME
Test / fork := true
Test / javaOptions += s"-Dgitbucket.home=${(Test / target).value / "gitbucket-home"}"