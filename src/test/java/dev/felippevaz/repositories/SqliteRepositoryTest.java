package dev.felippevaz.repositories;

import dev.felippevaz.annotations.Updatable;
import dev.felippevaz.database.SqliteDatabase;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.persistence.Id;
import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteRepositoryTest {

    public static class Delivery {

        @Id
        private String transactionId;

        private String player;

        @Updatable
        private String status;

        public Delivery() {
        }

        Delivery(String transactionId, String player, String status) {
            this.transactionId = transactionId;
            this.player = player;
            this.status = status;
        }
    }

    public static class Counter {

        @Id
        private Long id;

        @Updatable
        private int value;

        public Counter() {
        }

        Counter(Long id, int value) {
            this.id = id;
            this.value = value;
        }
    }

    static class DeliveryRepository extends SqliteRepository<Delivery, String> {
        DeliveryRepository(SqliteDatabase database) {
            super(database, "deliveries");
        }
    }

    static class CounterRepository extends SqliteRepository<Counter, Long> {
        CounterRepository(SqliteDatabase database) {
            super(database, "counters");
        }
    }

    @TempDir
    File tempDir;

    private SqliteDatabase database;
    private DeliveryRepository deliveries;

    @BeforeEach
    void open() {
        database = new SqliteDatabase(new File(tempDir, "nested/data.db"));
        deliveries = new DeliveryRepository(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void saveAndFind() {
        deliveries.save(new Delivery("tx1", "Felippe", "PENDING"));

        Delivery found = deliveries.findById("tx1");

        assertNotNull(found);
        assertEquals("Felippe", found.player);
        assertEquals("PENDING", found.status);
        assertNull(deliveries.findById("missing"));
        assertNull(deliveries.findById(null));
    }

    @Test
    void saveReplacesExistingEntity() {
        deliveries.save(new Delivery("tx1", "Felippe", "PENDING"));
        deliveries.save(new Delivery("tx1", "Felippe", "DELIVERED"));

        assertEquals(1, deliveries.count());
        assertEquals("DELIVERED", deliveries.findById("tx1").status);
    }

    @Test
    void findAllCountAndExists() {
        deliveries.save(new Delivery("tx1", "A", "PENDING"));
        deliveries.save(new Delivery("tx2", "B", "PENDING"));

        List<Delivery> all = deliveries.findAll();

        assertEquals(2, all.size());
        assertEquals(2, deliveries.count());
        assertTrue(deliveries.existsById("tx2"));
        assertFalse(deliveries.existsById("tx3"));
    }

    @Test
    void updateOnlyCopiesUpdatableFields() {
        deliveries.save(new Delivery("tx1", "Felippe", "PENDING"));

        Delivery payload = new Delivery("ignored", "Hacker", "DELIVERED");
        Delivery updated = deliveries.update("tx1", payload);

        assertEquals("DELIVERED", updated.status);
        assertEquals("Felippe", updated.player);
        assertEquals("tx1", updated.transactionId);

        Delivery stored = deliveries.findById("tx1");
        assertEquals("DELIVERED", stored.status);
        assertEquals("Felippe", stored.player);
    }

    @Test
    void updateMissingEntityFails() {
        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> deliveries.update("missing", new Delivery("missing", "x", "y")));

        assertEquals(Errors.ENTITY_NOT_FOUND, exception.getError());
    }

    @Test
    void saveWithoutIdFails() {
        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> deliveries.save(new Delivery(null, "x", "y")));

        assertEquals(Errors.ID_NOT_FOUND, exception.getError());
    }

    @Test
    void deleteById() {
        deliveries.save(new Delivery("tx1", "A", "PENDING"));

        deliveries.deleteById("tx1");
        deliveries.deleteById("not-there");
        deliveries.deleteById(null);

        assertEquals(0, deliveries.count());
    }

    @Test
    void dataSurvivesReopening() {
        deliveries.save(new Delivery("tx1", "Felippe", "DELIVERED"));
        database.close();

        database = new SqliteDatabase(new File(tempDir, "nested/data.db"));
        deliveries = new DeliveryRepository(database);

        assertEquals("DELIVERED", deliveries.findById("tx1").status);
    }

    @Test
    void retentionDeletesOldRecords() throws InterruptedException {
        deliveries.save(new Delivery("old", "A", "DELIVERED"));
        Thread.sleep(5);
        long cutoff = System.currentTimeMillis();
        Thread.sleep(5);
        deliveries.save(new Delivery("new", "B", "DELIVERED"));

        assertEquals(1, deliveries.deleteUpdatedBefore(cutoff));
        assertFalse(deliveries.existsById("old"));
        assertTrue(deliveries.existsById("new"));
    }

    @Test
    void multipleRepositoriesShareOneDatabase() {
        CounterRepository counters = new CounterRepository(database);

        counters.save(new Counter(1L, 10));
        deliveries.save(new Delivery("tx1", "A", "PENDING"));

        assertEquals(10, counters.findById(1L).value);
        assertEquals(1, counters.count());
        assertEquals(1, deliveries.count());
    }

    @Test
    void invalidTableNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SqliteRepository<Delivery, String>(database, "x; DROP TABLE y", Delivery.class) {
        });
    }

    @Test
    void closedDatabaseFails() {
        database.close();

        ApplicationException exception = assertThrows(ApplicationException.class, () -> deliveries.count());

        assertEquals(Errors.DATABASE_ERROR, exception.getError());
    }
}
