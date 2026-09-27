package io.github.gitbucket.backup.service

import io.github.gitbucket.backup.BackupConfFixture
import org.scalatest.funsuite.AnyFunSuite

class PluginSettingsServiceSpec extends AnyFunSuite with PluginSettingsService with BackupConfFixture {

  test("reads backup.conf as documented in the README") {
    writeBackupConf(
      """akka {
        |  quartz {
        |    schedules {
        |      Backup {
        |        expression = "0 0 0 * * ?"
        |        timezone = "Asia/Tokyo"
        |      }
        |    }
        |  }
        |}
        |backup {
        |  archive-destination = "/backups"
        |  archive-limit = 10
        |  notify-on-success = true
        |  notify-on-failure = true
        |  notify-dest = ["jyuch@localhost"]
        |  timeout = 60
        |  s3 {
        |    endpoint = "http://localhost:9000"
        |    region = "US_EAST_1"
        |    access-key = "access-key"
        |    secret-key = "secret-key"
        |    bucket = "gitbucket"
        |    archive-limit = 5
        |  }
        |}
        |""".stripMargin)

    val settings = loadPluginSettings()
    assert(settings.scheduleExpression == Some("0 0 0 * * ?"))
    assert(settings.scheduleTimezone == "Asia/Tokyo")
    assert(settings.archiveDestination == Some("/backups"))
    assert(settings.archiveLimit == Some(10))
    assert(settings.notifyOnSuccess)
    assert(settings.notifyOnFailure)
    assert(settings.notifyDestination == Some(List("jyuch@localhost")))
    assert(settings.timeoutMinutes == Some(60))
    assert(settings.endpoint == Some("http://localhost:9000"))
    assert(settings.region == Some("US_EAST_1"))
    assert(settings.accessKey == Some("access-key"))
    assert(settings.secretKey == Some("secret-key"))
    assert(settings.bucket == Some("gitbucket"))
    assert(settings.s3ArchiveLimit == Some(5))
  }

  test("uses akka.quartz.defaultTimezone when the schedule has no timezone") {
    writeBackupConf(
      """akka.quartz.defaultTimezone = "Europe/Berlin"
        |akka.quartz.schedules.Backup.expression = "0 0 0 * * ?"
        |""".stripMargin)
    assert(loadPluginSettings().scheduleTimezone == "Europe/Berlin")
  }

  test("defaults: timezone UTC, no timeout, no notifications, no schedule") {
    writeBackupConf("")
    val settings = loadPluginSettings()
    assert(settings.scheduleExpression == None)
    assert(settings.scheduleTimezone == "UTC")
    assert(settings.timeoutMinutes == None)
    assert(!settings.notifyOnSuccess)
    assert(!settings.notifyOnFailure)
  }
}
