package io.github.gitbucket.backup.task

import java.io.{PrintWriter, StringWriter}

import gitbucket.core.service.SystemSettingsService
import io.github.gitbucket.backup.service.PluginSettingsService
import org.apache.commons.mail.{DefaultAuthenticator, SimpleEmail}

object Mailer extends SystemSettingsService with PluginSettingsService {

  def testMail(): Unit = {
    send(
      "Test mail from Backup Plugin for Windows",
      "If you seen this message, mail settings has worked well.")
  }

  def backupSuccess(): Unit = {
    if (loadPluginSettings().notifyOnSuccess) {
      send(
        "Backup complete",
        "The backup ended normally.")
    }
  }

  def backupFailure(reason: Throwable): Unit = {
    if (loadPluginSettings().notifyOnFailure) {
      val sw = new StringWriter()
      reason.printStackTrace(new PrintWriter(sw))
      send(
        "Backup failure",
        sw.toString)
    }
  }

  def send(subject: String, body: String) : Unit = {
    val system = loadSystemSettings()
    loadPluginSettings().notifyDestination.foreach { dest =>
      if (dest.nonEmpty) {
        system.smtp.foreach { smtp =>
          val email = new SimpleEmail
          email.setHostName(smtp.host)
          smtp.port.foreach(email.setSmtpPort)
          for {
            user <- smtp.user
            pass <- smtp.password
          } {
            email.setAuthenticator(new DefaultAuthenticator(user, pass))
          }
          smtp.ssl.foreach(email.setSSLOnConnect)
          smtp.fromAddress.foreach(email.setFrom)
          email.setSubject(subject)
          email.setMsg(body)
          dest.foreach(email.addTo)
          email.send()
        }
      }
    }
  }
}
