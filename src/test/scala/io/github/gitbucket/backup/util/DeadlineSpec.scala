package io.github.gitbucket.backup.util

import java.io.File
import java.util.concurrent.TimeoutException

import org.scalatest.funsuite.AnyFunSuite

class DeadlineSpec extends AnyFunSuite {

  private val expired = new Deadline(Some(1), startMillis = System.currentTimeMillis - 61 * 1000L)

  test("is disabled when no timeout or a timeout <= 0 is configured") {
    Seq(None, Some(0), Some(-1)).foreach { timeout =>
      val deadline = new Deadline(timeout, startMillis = 0L)
      assert(!deadline.isExpired)
      assert(deadline.remainingMillis == None)
      deadline.check()
    }
  }

  test("is not expired before the timeout") {
    val deadline = new Deadline(Some(1))
    assert(!deadline.isExpired)
    assert(deadline.remainingMillis.exists(ms => ms > 0 && ms <= 60 * 1000L))
    deadline.check()
  }

  test("throws a TimeoutException after the timeout") {
    assert(expired.isExpired)
    assert(expired.remainingMillis == Some(1L))
    val e = intercept[TimeoutException](expired.check())
    assert(e.getMessage.contains("1 minutes"))
  }

  test("fileFilter accepts files until the deadline and then stops the copy") {
    assert(new Deadline(Some(1)).fileFilter.accept(new File("x")))
    intercept[TimeoutException](expired.fileFilter.accept(new File("x")))
  }
}
