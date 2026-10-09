package dev.felippevaz.database;

import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Conexão com um arquivo SQLite, compartilhada pelos {@code SqliteRepository}.
 * <p>
 * O SQLite aceita um único escritor por vez, então todo acesso passa por uma única
 * conexão protegida por lock: simples, sem "database is locked" entre threads e
 * mais que suficiente para o volume de um serviço pequeno.
 * <p>
 * O driver ({@code org.sqlite.JDBC}) não é embutido no framework: ele precisa estar
 * no classpath de quem usa (o Spigot/Paper, por exemplo, já traz um).
 */
public class SqliteDatabase implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SqliteDatabase.class.getName());

    private static final String DRIVER_CLASS = "org.sqlite.JDBC";

    private final File file;
    private final ReentrantLock lock = new ReentrantLock();

    private Connection connection;
    private boolean closed = false;

    public SqliteDatabase(File file) {
        this.file = file.getAbsoluteFile();
    }

    public SqliteDatabase(String path) {
        this(new File(path));
    }

    /**
     * Executa um trabalho com a conexão, com acesso exclusivo.
     *
     * @throws ApplicationException {@link Errors#DATABASE_ERROR} em qualquer {@link SQLException}.
     */
    public <R> R execute(SqlFunction<Connection, R> work) {

        lock.lock();
        try {
            return work.apply(connection());
        } catch (SQLException exception) {
            LOGGER.log(Level.SEVERE, "SQLite error on " + file.getName(), exception);
            throw new ApplicationException(Errors.DATABASE_ERROR, exception);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Igual a {@link #execute(SqlFunction)}, mas dentro de uma transação:
     * commit se o trabalho terminar normalmente, rollback se lançar qualquer exceção.
     */
    public <R> R transaction(SqlFunction<Connection, R> work) {

        return execute(connection -> {

            connection.setAutoCommit(false);

            try {
                R result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error exception) {
                rollbackQuietly(connection);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    public File getFile() {
        return file;
    }

    @Override
    public void close() {

        lock.lock();
        try {

            closed = true;

            if (connection != null) {
                connection.close();
                connection = null;
                LOGGER.fine(() -> "Closed SQLite database " + file.getName());
            }

        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Failed to close SQLite database " + file.getName(), exception);
        } finally {
            lock.unlock();
        }
    }

    // Abre (ou reabre) a conexão sob demanda. Só é chamado com o lock adquirido.
    private Connection connection() throws SQLException {

        if (closed)
            throw new SQLException("SqliteDatabase " + file.getName() + " is closed");

        if (connection != null && !connection.isClosed())
            return connection;

        loadDriver();

        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new SQLException("Could not create directory " + parent);

        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getPath());

        // execute() e não executeUpdate(): alguns PRAGMAs devolvem uma linha, o que
        // drivers antigos (como o 3.7.x do Spigot 1.8) tratam como erro em executeUpdate.
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }

        LOGGER.fine(() -> "Opened SQLite database " + file.getPath());

        return connection;
    }

    private static void loadDriver() {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException exception) {
            LOGGER.severe("SQLite JDBC driver (" + DRIVER_CLASS + ") not found on the classpath");
            throw new ApplicationException(Errors.DATABASE_DRIVER_NOT_FOUND, exception);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            LOGGER.log(Level.WARNING, "Rollback failed", exception);
        }
    }
}
