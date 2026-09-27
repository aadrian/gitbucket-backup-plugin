package io.github.gitbucket.backup.task

import java.io.File

import gitbucket.core.util.{Directory => gDirectory}
import io.github.gitbucket.backup.util.{Deadline, Directory}
import io.github.gitbucket.backup.service.PluginSettingsService
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.TrueFileFilter
import org.slf4j.LoggerFactory
import org.zeroturnaround.zip.ZipUtil

import scala.jdk.CollectionConverters._

object Archiver extends PluginSettingsService {

  private val logger = LoggerFactory.getLogger(getClass)

  def run(tempBackupDir: File, backupName: String, deadline: Deadline): Unit = {
    val config = loadPluginSettings()

    val srcDataDir = new File(gDirectory.DatabaseHome)
    val data = Directory.getDataBackupDir(tempBackupDir)
    if (srcDataDir.exists()) {
      FileUtils.copyDirectory(srcDataDir, data, deadline.fileFilter)
    }

    val zip = new File(config.archiveDestination.getOrElse(gDirectory.GitBucketHome), s"$backupName.zip")
    try {
      ZipUtil.pack(tempBackupDir, zip, { name: String =>
        deadline.check()
        name
      })
    } catch {
      case e: Throwable =>
        zip.delete()
        throw e
    }

    config.archiveLimit foreach { n =>
      if (n > 0) {
        val pattern = """^backup-\d{12}\.zip$""".r
        val d = new File(config.archiveDestination.getOrElse(gDirectory.GitBucketHome))
        val t = d.listFiles.filter(
          _.getName match {
            case pattern() => true
            case _ => false
          }).sortBy(_.getName).reverse.drop(n)

        t foreach { f =>
          logger.info("Delete archive {}", f.getAbsoluteFile)
          f.delete()
        }
      }
    }
  }

  def deleteTempDir(tempBackupDir: File): Unit = {
    if (tempBackupDir.exists()) {
      val tempDirectoryEntries = FileUtils.iterateFiles(tempBackupDir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE).asScala
      for (it <- tempDirectoryEntries) {
        // In Windows, can't delete temp dir because index file marked as readonly.
        it.setWritable(true)
      }
      FileUtils.deleteDirectory(tempBackupDir)
    }
  }
}
