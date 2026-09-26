package io.github.gitbucket.backup.service

import java.io.File
import java.util.{Date, TimeZone}
import java.util.concurrent.{Executors, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import gitbucket.core.util.{Directory => gDirectory}
import io.github.gitbucket.backup.task.{Archiver, DatabaseDump, Mailer, ObjectStorage, RepositoryClone}
import io.github.gitbucket.backup.util.{Deadline, Directory}
import org.quartz.CronExpression
import org.slf4j.LoggerFactory

object BackupService extends PluginSettingsService {

  private val logger = LoggerFactory.getLogger(getClass)

  // One thread fires the cron schedule and sends test mails, the other runs backups one at a time
  private val scheduler = Executors.newSingleThreadScheduledExecutor(r => new Thread(r, "gitbucket-backup-scheduler"))
  private val worker = Executors.newSingleThreadExecutor(r => new Thread(r, "gitbucket-backup"))
  private val busy = new AtomicBoolean(false)

  def initialize(): Unit = {
    val config = loadPluginSettings()
    config.scheduleExpression match {
      case Some(expression) =>
        val cron = new CronExpression(expression)
        cron.setTimeZone(TimeZone.getTimeZone(config.scheduleTimezone))
        scheduleNext(cron)
      case None =>
        logger.info("No backup schedule configured, backups only run via the API")
    }
  }

  def teardown(): Unit = {
    scheduler.shutdownNow()
    worker.shutdownNow()
    // Wait for a running backup because the ClassLoader is closed when the plugin shuts down
    worker.awaitTermination(1, TimeUnit.MINUTES)
  }

  def sendTestMail(): Unit = {
    scheduler.execute { () =>
      try {
        Mailer.testMail()
      } catch {
        case e: Exception => logger.error("Failed to send the test mail", e)
      }
    }
  }

  def executeBackup(): Unit = {
    if (busy.compareAndSet(false, true)) {
      worker.execute(() => try runBackup() finally busy.set(false))
    } else {
      logger.warn("A backup is already running, skipped")
    }
  }

  private def scheduleNext(cron: CronExpression): Unit = {
    Option(cron.getNextValidTimeAfter(new Date)).foreach { next =>
      val task: Runnable = () => {
        executeBackup()
        scheduleNext(cron)
      }
      scheduler.schedule(task, next.getTime - System.currentTimeMillis, TimeUnit.MILLISECONDS)
    }
  }

  private def runBackup(): Unit = {
    val backupName = Directory.getBackupName
    val tempBackupDir = new File(gDirectory.GitBucketHome, backupName)
    val deadline = new Deadline(loadPluginSettings().timeoutMinutes)
    val failure = try {
      val repos = DatabaseDump.run(tempBackupDir)
      repos.foreach { case (user, repo) =>
        deadline.check()
        RepositoryClone.run(tempBackupDir, user, repo, deadline)
      }
      deadline.check()
      Archiver.run(tempBackupDir, backupName, deadline)
      deadline.check()
      ObjectStorage.run(backupName, deadline)
      logger.info("Backup complete")
      None
    } catch {
      case e: Throwable =>
        logger.error("Backup failed", e)
        Some(e)
    } finally {
      try {
        Archiver.deleteTempDir(tempBackupDir)
      } catch {
        case e: Exception => logger.error(s"Failed to delete the temporary directory $tempBackupDir", e)
      }
    }

    try {
      failure.fold(Mailer.backupSuccess())(Mailer.backupFailure)
    } catch {
      case e: Exception => logger.error("Failed to send the backup notification mail", e)
    }
  }
}
