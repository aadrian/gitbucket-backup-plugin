package io.github.gitbucket.backup

import java.nio.charset.StandardCharsets

import io.github.gitbucket.backup.util.Directory
import org.apache.commons.io.FileUtils

/** Writes GITBUCKET_HOME/backup.conf (the test GITBUCKET_HOME is set in build.sbt). */
trait BackupConfFixture {
  def writeBackupConf(content: String): Unit = {
    FileUtils.writeStringToFile(Directory.BackupConf, content, StandardCharsets.UTF_8)
  }
}
