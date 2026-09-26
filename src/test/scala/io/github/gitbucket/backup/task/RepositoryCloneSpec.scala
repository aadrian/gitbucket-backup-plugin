package io.github.gitbucket.backup.task

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.TimeoutException

import gitbucket.core.util.{Directory => gDirectory}
import io.github.gitbucket.backup.util.{Deadline, Directory}
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.{Constants, ObjectId, PersonIdent}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite

import scala.util.Using

class RepositoryCloneSpec extends AnyFunSuite with BeforeAndAfterEach {

  private val baseDir = new File(gDirectory.GitBucketHome, "backup-209912312359")

  override def beforeEach(): Unit = {
    FileUtils.deleteQuietly(baseDir)
    FileUtils.deleteQuietly(new File(gDirectory.RepositoryHome, "alice"))
  }

  // Creates a bare repository with one commit, like GitBucket's repositories
  private def createRepository(dir: File): ObjectId = {
    val work = Files.createTempDirectory("work").toFile
    try {
      val commitId = Using.resource(Git.init().setDirectory(work).call()) { git =>
        FileUtils.writeStringToFile(new File(work, "README.md"), "hello", UTF_8)
        git.add().addFilepattern("README.md").call()
        val ident = new PersonIdent("Alice", "alice@example.com")
        git.commit().setMessage("init").setAuthor(ident).setCommitter(ident).setSign(false).call().getId
      }
      Using.resource(Git.cloneRepository().setURI(work.toURI.toString).setDirectory(dir).setBare(true).call())(_ => ())
      commitId
    } finally {
      FileUtils.deleteQuietly(work)
    }
  }

  test("clones the repository and the wiki as bare repositories and copies the attached files") {
    val repoCommit = createRepository(gDirectory.getRepositoryDir("alice", "project"))
    val wikiCommit = createRepository(gDirectory.getWikiRepositoryDir("alice", "project"))
    FileUtils.writeStringToFile(new File(gDirectory.getRepositoryFilesDir("alice", "project"), "issues/1/a.txt"), "a", UTF_8)

    RepositoryClone.run(baseDir, "alice", "project", new Deadline(None))

    Seq(
      Directory.getRepositoryBackupDir(baseDir, "alice", "project") -> repoCommit,
      Directory.getWikiBackupDir(baseDir, "alice", "project") -> wikiCommit
    ).foreach { case (dir, commit) =>
      Using.resource(Git.open(dir)) { git =>
        val repository = git.getRepository
        assert(repository.isBare)
        assert(repository.resolve(Constants.HEAD) == commit)
        assert(repository.getConfig.getBoolean("http", null, "receivepack", false))
      }
    }
    assert(new File(Directory.getRepositoryFilesBackupDir(baseDir, "alice", "project"), "issues/1/a.txt").exists)
  }

  test("stops with a TimeoutException when the deadline has passed") {
    createRepository(gDirectory.getRepositoryDir("alice", "project"))
    createRepository(gDirectory.getWikiRepositoryDir("alice", "project"))
    val expired = new Deadline(Some(1), startMillis = System.currentTimeMillis - 61 * 1000L)

    intercept[TimeoutException](RepositoryClone.run(baseDir, "alice", "project", expired))
  }
}
