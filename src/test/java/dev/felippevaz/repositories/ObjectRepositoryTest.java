package dev.felippevaz.repositories;

import dev.felippevaz.annotations.Updatable;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;
import org.junit.jupiter.api.Test;

import javax.persistence.Id;

import static org.junit.jupiter.api.Assertions.*;

class ObjectRepositoryTest {

    static class Product {

        @Id
        Long id;

        @Updatable
        String name;

        String internalFlag;

        Product(Long id, String name, String internalFlag) {
            this.id = id;
            this.name = name;
            this.internalFlag = internalFlag;
        }
    }

    static class ProductRepository extends ObjectRepository<Product, Long> {
    }

    @Test
    void crud() {
        ProductRepository repository = new ProductRepository();

        repository.save(new Product(1L, "Teclado", "a"));

        assertTrue(repository.existsById(1L));
        assertEquals(1, repository.count());

        Product updated = repository.update(1L, new Product(99L, "Teclado mecânico", "hacked"));

        assertEquals("Teclado mecânico", updated.name);
        assertEquals("a", updated.internalFlag);
        assertEquals(1L, updated.id);

        repository.deleteById(1L);
        assertEquals(0, repository.count());
    }

    @Test
    void errors() {
        ProductRepository repository = new ProductRepository();

        assertEquals(Errors.ID_NOT_FOUND,
                assertThrows(ApplicationException.class, () -> repository.save(new Product(null, "x", "y"))).getError());
        assertEquals(Errors.ENTITY_NOT_FOUND,
                assertThrows(ApplicationException.class, () -> repository.update(5L, new Product(5L, "x", "y"))).getError());
    }
}
