package dev.felippevaz.repositories;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.felippevaz.database.SqliteDatabase;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.http.HttpUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Repositório persistido em SQLite, com o mesmo contrato do {@link ObjectRepository}.
 * <p>
 * Cada repositório usa uma tabela própria no formato
 * {@code (id TEXT PRIMARY KEY, data TEXT, updated_at INTEGER)}: a entidade inteira é
 * guardada em JSON (Gson) na coluna {@code data}. Não há mapeamento de colunas nem
 * migrações: adicionar um campo na classe basta.
 *
 * <pre>{@code
 * public class DeliveryRepository extends SqliteRepository<Delivery, String> {
 *     public DeliveryRepository(SqliteDatabase database) {
 *         super(database, "deliveries");
 *     }
 * }
 * }</pre>
 *
 * Para consultas que o contrato genérico não cobre, subclasses podem usar
 * {@link #getDatabase()}, {@link #getTable()} e {@link #fromJson(String)}.
 */
public abstract class SqliteRepository<T, ID> implements Repository<T, ID> {

    private static final Logger LOGGER = Logger.getLogger(SqliteRepository.class.getName());

    // O nome da tabela entra direto no SQL (não dá para usar parâmetro), então só
    // identificadores simples são aceitos.
    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final SqliteDatabase database;
    private final String table;
    private final Class<T> type;
    private final Gson gson;

    /** Tipo da entidade inferido pelo generic da subclasse. */
    protected SqliteRepository(SqliteDatabase database, String table) {
        this(database, table, null, HttpUtils.GSON);
    }

    protected SqliteRepository(SqliteDatabase database, String table, Class<T> type) {
        this(database, table, type, HttpUtils.GSON);
    }

    protected SqliteRepository(SqliteDatabase database, String table, Class<T> type, Gson gson) {

        if (table == null || !TABLE_NAME.matcher(table).matches())
            throw new IllegalArgumentException("Invalid table name: " + table);

        this.database = database;
        this.table = table;
        this.type = type != null ? type : inferEntityType();
        this.gson = gson;

        createTable();
    }

    @Override
    public List<T> findAll() {
        return database.execute(connection -> {

            List<T> entities = new ArrayList<>();

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT data FROM " + table + " ORDER BY updated_at, id")) {

                while (result.next())
                    entities.add(fromJson(result.getString(1)));
            }

            return entities;
        });
    }

    @Override
    public T findById(ID id) {

        if (id == null)
            return null;

        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT data FROM " + table + " WHERE id = ?")) {

                statement.setString(1, key(id));

                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? fromJson(result.getString(1)) : null;
                }
            }
        });
    }

    @Override
    public boolean existsById(ID id) {

        if (id == null)
            return false;

        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {

                statement.setString(1, key(id));

                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    @Override
    public long count() {
        return database.execute(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                return result.next() ? result.getLong(1) : 0L;
            }
        });
    }

    @Override
    public T save(T entity) {

        String id = key(Entities.extractId(entity));
        String json = toJson(entity);

        database.execute(connection -> {
            write(connection, id, json);
            return null;
        });

        return entity;
    }

    @Override
    public T update(ID id, T updatedEntity) {

        if (id == null)
            throw new ApplicationException(Errors.ENTITY_NOT_FOUND, null);

        String key = key(id);

        // Leitura + escrita na mesma transação: nenhum outro save() entra no meio.
        return database.transaction(connection -> {

            T entity;

            try (PreparedStatement statement = connection.prepareStatement("SELECT data FROM " + table + " WHERE id = ?")) {

                statement.setString(1, key);

                try (ResultSet result = statement.executeQuery()) {

                    if (!result.next())
                        throw new ApplicationException(Errors.ENTITY_NOT_FOUND, null);

                    entity = fromJson(result.getString(1));
                }
            }

            Entities.copyUpdatableFields(updatedEntity, entity);
            write(connection, key, toJson(entity));

            return entity;
        });
    }

    @Override
    public void deleteById(ID id) {

        if (id == null)
            return;

        database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
                statement.setString(1, key(id));
                statement.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Remove os registros salvos/atualizados pela última vez antes do instante informado.
     * Útil para políticas de retenção (ex.: apagar registros com mais de 90 dias).
     *
     * @return quantidade de registros removidos.
     */
    public int deleteUpdatedBefore(long epochMillis) {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE updated_at < ?")) {
                statement.setLong(1, epochMillis);
                return statement.executeUpdate();
            }
        });
    }

    protected SqliteDatabase getDatabase() {
        return database;
    }

    protected String getTable() {
        return table;
    }

    protected Class<T> getEntityType() {
        return type;
    }

    protected T fromJson(String json) {
        try {
            return gson.fromJson(json, type);
        } catch (JsonParseException exception) {
            throw new ApplicationException(Errors.DATABASE_ERROR, exception);
        }
    }

    protected String toJson(T entity) {
        return gson.toJson(entity);
    }

    private void write(Connection connection, String id, String json) throws SQLException {
        // INSERT OR REPLACE em vez de UPSERT (ON CONFLICT ... DO UPDATE): o UPSERT só
        // existe a partir do SQLite 3.24, e o Spigot 1.8 traz o driver 3.7.x.
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO " + table + " (id, data, updated_at) VALUES (?, ?, ?)")) {

            statement.setString(1, id);
            statement.setString(2, json);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void createTable() {
        database.execute(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + table
                        + " (id TEXT PRIMARY KEY NOT NULL, data TEXT NOT NULL, updated_at INTEGER NOT NULL)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_" + table + "_updated_at ON " + table + " (updated_at)");
            }
            return null;
        });

        LOGGER.fine(() -> "SQLite table '" + table + "' ready for " + type.getSimpleName());
    }

    private static String key(Object id) {
        return String.valueOf(id);
    }

    @SuppressWarnings("unchecked")
    private Class<T> inferEntityType() {

        for (Class<?> current = getClass(); current != null && current != Object.class; current = current.getSuperclass()) {

            Type superType = current.getGenericSuperclass();

            if (superType instanceof ParameterizedType
                    && ((ParameterizedType) superType).getRawType() == SqliteRepository.class) {

                Type entityType = ((ParameterizedType) superType).getActualTypeArguments()[0];

                if (entityType instanceof Class)
                    return (Class<T>) entityType;

                if (entityType instanceof ParameterizedType)
                    return (Class<T>) ((ParameterizedType) entityType).getRawType();

                break;
            }
        }

        throw new IllegalStateException("Could not infer the entity type of " + getClass().getName()
                + "; pass it explicitly to the SqliteRepository constructor");
    }
}
