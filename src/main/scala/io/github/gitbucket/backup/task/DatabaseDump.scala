package io.github.gitbucket.backup.task

import java.io.File

import gitbucket.core.model.Profile.profile.blockingApi._
import gitbucket.core.service.{AccountService, RepositoryService}
import gitbucket.core.servlet.Database
import gitbucket.core.util.JDBCUtil.RichConnection
import org.apache.commons.io.FileUtils

object DatabaseDump extends AccountService with RepositoryService {

  /** Dumps the database into baseDir and returns the (user, repository) pairs to back up. */
  def run(baseDir: File): List[(String, String)] = {
    Database() withTransaction { implicit session =>
      val allTables = session.conn.allTableNames()
      val sqlFile = session.conn.exportAsSQL(allTables)
      val sqlBackup = new File(baseDir, "gitbucket.sql")
      FileUtils.copyFile(sqlFile, sqlBackup)
      sqlFile.delete()

      for {
        user <- getAllUsers()
        repo <- getRepositoryNamesOfUser(user.userName)
      } yield {
        (user.userName, repo)
      }
    }
  }
}
