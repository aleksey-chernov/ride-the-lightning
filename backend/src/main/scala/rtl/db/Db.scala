package rtl.db

import com.augustnagro.magnum
import com.augustnagro.magnum.{DbTx, Transactor}
import org.flywaydb.core.Flyway
import org.sqlite.SQLiteConfig.{JournalMode, SynchronousMode}
import org.sqlite.{SQLiteConfig, SQLiteDataSource}
import ox.discard

import java.nio.file.{Files, Path}
import javax.sql.DataSource

/** A SQLite database file. Every `transact` opens its own connection, so one writer and many readers can overlap. */
class Db(dataSource: DataSource):
  private val transactor = Transactor(dataSource)

  /** Runs `f` in one transaction: committed when it returns, rolled back when it throws. */
  def transact[T](f: DbTx ?=> T): T = magnum.transact(transactor)(f)

object Db:
  /** Opens the file (creating it and its folder if needed) and applies pending migrations from `db/migration`. */
  def open(file: Path): Db =
    Files.createDirectories(file.toAbsolutePath.getParent)
    // applied to every new connection
    val config = SQLiteConfig()
    config.setJournalMode(JournalMode.WAL) // readers don't block the writer and vice versa
    config.setSynchronous(SynchronousMode.NORMAL) // safe with WAL; a power cut may lose the last commits only
    config.setBusyTimeout(5000) // wait up to 5 s for a lock instead of failing at once
    val dataSource = SQLiteDataSource(config)
    dataSource.setUrl(s"jdbc:sqlite:${file.toAbsolutePath}")
    Flyway.configure().dataSource(dataSource).load().migrate().discard
    Db(dataSource)
