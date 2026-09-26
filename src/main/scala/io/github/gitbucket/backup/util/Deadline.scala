package io.github.gitbucket.backup.util

import java.io.FileFilter
import java.util.concurrent.TimeoutException

/**
 * Time limit for a whole backup run (`backup.timeout` in minutes, disabled when unset or <= 0).
 * Steps call `check()` at points where they can stop cleanly.
 */
class Deadline(timeoutMinutes: Option[Int]) {
  private val at = timeoutMinutes.filter(_ > 0).map(m => System.currentTimeMillis + m * 60 * 1000L)

  def isExpired: Boolean = at.exists(System.currentTimeMillis >= _)

  def remainingMillis: Option[Long] = at.map(t => math.max(t - System.currentTimeMillis, 1L))

  def check(): Unit = {
    if (isExpired) {
      throw new TimeoutException(s"Backup exceeded the time limit of ${timeoutMinutes.get} minutes")
    }
  }

  // Accepts every file, but stops a copy once the deadline has passed
  val fileFilter: FileFilter = { _ =>
    check()
    true
  }
}
