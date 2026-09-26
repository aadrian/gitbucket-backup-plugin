package io.github.gitbucket.backup.task

import java.io.File

import gitbucket.core.util.{Directory => gDirectory}
import io.github.gitbucket.backup.util.{Deadline, Directory}
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.EmptyProgressMonitor
import org.slf4j.LoggerFactory

import scala.util.Using

object RepositoryClone {

  private val logger = LoggerFactory.getLogger(getClass)

  def run(baseDir: File, user: String, repo: String, deadline: Deadline): Unit = {
    val src = gDirectory.getRepositoryDir(user, repo)
    val dest = Directory.getRepositoryBackupDir(baseDir, user, repo)
    cloneRepository(src, dest, deadline)

    val wikiSrc = gDirectory.getWikiRepositoryDir(user, repo)
    val wikiDest = Directory.getWikiBackupDir(baseDir, user, repo)
    cloneRepository(wikiSrc, wikiDest, deadline)

    val filesSrc = gDirectory.getRepositoryFilesDir(user, repo)
    val filesDest = Directory.getRepositoryFilesBackupDir(baseDir, user, repo)
    if (filesSrc.exists() && filesSrc.isDirectory) {
      FileUtils.copyDirectory(filesSrc, filesDest, deadline.fileFilter)
    }

    logger.info("Clone repository {}/{}", user, repo)
  }

  // Same as JGitUtil.cloneRepository() but can be cancelled when the deadline has passed
  private def cloneRepository(from: File, to: File, deadline: Deadline): Unit = {
    val monitor = new EmptyProgressMonitor {
      override def isCancelled: Boolean = deadline.isExpired
    }
    try {
      Using.resource(Git.cloneRepository.setURI(from.toURI.toString).setDirectory(to).setBare(true).setProgressMonitor(monitor).call) { git =>
        val config = git.getRepository.getConfig
        config.setBoolean("http", null, "receivepack", true)
        config.save()
      }
    } finally {
      deadline.check()
    }
  }
}
