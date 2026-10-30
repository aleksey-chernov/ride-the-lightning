package rtl

import com.augustnagro.magnum.sql
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import ox.discard
import rtl.db.Db

import java.nio.file.{Files, Path}
import java.util.Comparator

/** A real, migrated SQLite file per suite, emptied before each test and deleted afterwards. */
trait TestDb extends BeforeAndAfterAll with BeforeAndAfterEach:
  self: Suite =>
  private val dir: Path = Files.createTempDirectory("rtl-history")
  val db: Db = Db.open(dir.resolve("history.db"))

  override def beforeEach(): Unit =
    super.beforeEach()
    db.transact(sql"DELETE FROM strikes".update.run()).discard

  override def afterAll(): Unit =
    try Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(Files.delete)
    finally super.afterAll()
