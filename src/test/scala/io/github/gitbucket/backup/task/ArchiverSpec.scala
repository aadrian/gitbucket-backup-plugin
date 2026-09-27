package io.github.gitbucket.backup.task

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeoutException

import gitbucket.core.util.{Directory => gDirectory}
import io.github.gitbucket.backup.BackupConfFixture
import io.github.gitbucket.backup.util.Deadline
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.zeroturnaround.zip.ZipUtil

class ArchiverSpec extends AnyFunSuite with BackupConfFixture with BeforeAndAfterEach {

  private val home = new File(gDirectory.GitBucketHome)
  private val dest = new File(home, "archives")
  private val dataDir = new File(gDirectory.DatabaseHome)
  private val backupName = "backup-209912312359"
  private val tempBackupDir = new File(home, backupName)
  private val expired = new Deadline(Some(1), startMillis = System.currentTimeMillis - 61 * 1000L)

  override def beforeEach(): Unit = {
    Seq(dest, dataDir, tempBackupDir).foreach(FileUtils.deleteQuietly)
    dest.mkdirs()
    FileUtils.writeStringToFile(new File(tempBackupDir, "gitbucket.sql"), "DELETE FROM ACCOUNT;", UTF_8)
    writeBackupConf(
      s"""backup {
         |  archive-destination = \"\"\"${dest.getAbsolutePath}\"\"\"
         |  archive-limit = 2
         |}
         |""".stripMargin)
  }

  test("zips the backup with GitBucket's data directory and keeps only the newest archives") {
    FileUtils.writeStringToFile(new File(dataDir, "avatars/alice.png"), "png", UTF_8)
    Seq("backup-202601010000.zip", "backup-202601020000.zip", "backup-manual.zip", "notes.txt")
      .foreach(name => FileUtils.touch(new File(dest, name)))

    Archiver.run(tempBackupDir, backupName, new Deadline(None))

    val zip = new File(dest, s"$backupName.zip")
    assert(ZipUtil.containsEntry(zip, "gitbucket.sql"))
    assert(ZipUtil.containsEntry(zip, "data/avatars/alice.png"))
    // only backup-<12 digits>.zip files are rotated, anything else is left alone
    assert(dest.list.toSet == Set(s"$backupName.zip", "backup-202601020000.zip", "backup-manual.zip", "notes.txt"))
  }

  test("stops when the deadline has passed and leaves no partial archive") {
    intercept[TimeoutException](Archiver.run(tempBackupDir, backupName, expired))
    assert(dest.list.isEmpty)
  }

  test("deleteTempDir also removes read-only files") {
    val file = new File(tempBackupDir, "repositories/alice/project.git/objects/pack/pack.idx")
    FileUtils.writeStringToFile(file, "idx", UTF_8)
    file.setReadOnly()

    Archiver.deleteTempDir(tempBackupDir)
    assert(!tempBackupDir.exists)
  }
}
