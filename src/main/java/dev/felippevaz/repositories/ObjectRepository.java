package dev.felippevaz.repositories;

import dev.felippevaz.annotations.Updatable;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ObjectRepository<T, ID> {

    private static final Logger LOGGER = Logger.getLogger(ObjectRepository.class.getName());

    protected final Map<ID, T> entityManager;

    public ObjectRepository() {
        this.entityManager = new ConcurrentHashMap<>();
    }

    public List<T> findAll() {
        return new ArrayList<>(entityManager.values());
    }

    public T findById(ID id) {
        return entityManager.get(id);
    }

    // Apenas campos anotados com @Updatable são copiados do payload do cliente para
    // a entidade persistida. Isto evita "mass assignment": sem allow-list explícita,
    // qualquer campo do objeto (ids, flags internas, etc.) poderia ser sobrescrito
    // por um payload malicioso.
    public T update(ID id, T updatedEntity) {

        T entity = entityManager.get(id);

        if(entity == null)
            throw new ApplicationException(Errors.ENTITY_NOT_FOUND, null);

        Class<?> classEntity = entity.getClass();
        boolean anyFieldUpdated = false;

        for(Field field : classEntity.getDeclaredFields()) {

            if(Modifier.isStatic(field.getModifiers()))
                continue;

            if(!field.isAnnotationPresent(Updatable.class))
                continue;

            field.setAccessible(true);

            try {

                Object value = field.get(updatedEntity);
                field.set(entity, value);
                anyFieldUpdated = true;

            } catch (IllegalAccessException exception) {
                LOGGER.log(Level.SEVERE, "Failed to copy field '" + field.getName()
                        + "' during update of " + classEntity.getSimpleName(), exception);
                throw new ApplicationException(Errors.FIELD_COPY_ERROR, exception);
            }
        }

        if(!anyFieldUpdated)
            LOGGER.warning(() -> "update() called on " + classEntity.getSimpleName()
                    + " but no field is annotated with @Updatable - nothing was changed");

        return entity;
    }

    public T save(T entity) {
        try {

            for (Field field : entity.getClass().getDeclaredFields()) {

                if (!field.isAnnotationPresent(javax.persistence.Id.class))
                    continue;

                field.setAccessible(true);
                Object value = field.get(entity);

                if (value == null)
                    continue;

                ID id = (ID) value;

                this.entityManager.put(id, entity);

                return entity;
            }

            throw new ApplicationException(Errors.ID_NOT_FOUND, null);

        } catch (IllegalAccessException | IllegalArgumentException exception) {
            LOGGER.log(Level.SEVERE, "Failed to save entity of type " + entity.getClass().getSimpleName(), exception);
            throw new ApplicationException(Errors.FIELD_COPY_ERROR, exception);
        }
    }

    public void deleteById(ID id) {

        T entity = entityManager.get(id);

        if(entity == null)
            return;

        entityManager.remove(id, entity);
    }
}
