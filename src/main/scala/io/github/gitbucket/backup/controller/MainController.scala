package io.github.gitbucket.backup.controller

import gitbucket.core.controller.ControllerBase
import gitbucket.core.util.AdminAuthenticator
import io.github.gitbucket.backup.service.BackupService
import org.scalatra.Ok

class MainController extends ControllerBase with AdminAuthenticator {

  post("/api/v3/backup/mail-test") {
    adminOnly {
      BackupService.sendTestMail()
      Ok()
    }
  }

  post("/api/v3/backup/execute") {
    adminOnly {
      BackupService.executeBackup()
      Ok()
    }
  }
}
