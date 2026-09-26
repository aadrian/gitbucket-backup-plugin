package io.github.gitbucket.backup.service

import java.time.{ZoneId, ZonedDateTime}
import java.util.Date

import io.github.gitbucket.backup.BackupConfFixture
import org.scalatest.funsuite.AnyFunSuite

class BackupServiceSpec extends AnyFunSuite with PluginSettingsService with BackupConfFixture {

  private def nextRun(conf: String, after: ZonedDateTime): Option[ZonedDateTime] = {
    writeBackupConf(conf)
    BackupService.cronExpression(loadPluginSettings()).map { cron =>
      cron.getNextValidTimeAfter(Date.from(after.toInstant)).toInstant.atZone(ZoneId.of("UTC"))
    }
  }

  private val start = ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneId.of("UTC"))

  test("runs at the configured time in the configured timezone") {
    val next = nextRun(
      """akka.quartz.schedules.Backup {
        |  expression = "0 0 0 * * ?"
        |  timezone = "Asia/Tokyo"
        |}
        |""".stripMargin, start)
    // midnight in Tokyo is 15:00 UTC
    assert(next == Some(ZonedDateTime.of(2026, 1, 1, 15, 0, 0, 0, ZoneId.of("UTC"))))
  }

  test("uses UTC when no timezone is configured, like akka-quartz-scheduler did") {
    val next = nextRun("""akka.quartz.schedules.Backup.expression = "0 0 0 * * ?"""", start)
    assert(next == Some(ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC"))))
  }

  test("has no schedule when no expression is configured") {
    assert(nextRun("", start) == None)
  }
}
