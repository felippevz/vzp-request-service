package dev.felippevaz.repositories;

import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.exceptions.GlobalException;

import javax.persistence.EntityNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class ObjectRepository<T, ID> {

    protected final HashMap<ID, T> entityManager;

    public ObjectRepository() {
        this.entityManager = new HashMap<>();
    }

    public List<T> findAll() {
        return new ArrayList<>(entityManager.values());
    }

    public T findById(ID id) {
        return entityManager.get(id);
    }

    public T update(ID id, T updatedEntity, String ignore) {

        T entity = entityManager.get(id);

        if(entity == null)
            //todo: treat error later
            throw new EntityNotFoundException();

        Class<?> classEntity = entity.getClass();

        for(Field field : classEntity.getDeclaredFields()) {

            if(Modifier.isStatic(field.getModifiers()))
                continue;

            if(field.getName().equals(ignore))
                continue;

            field.setAccessible(true);

            try {

                Object value = field.get(updatedEntity);
                field.set(entity, value);

            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return entity;
    }

    @SuppressWarnings("unchecked")
    public T save(T entity) {
        try {

            for (Field field : entity.getClass().getDeclaredFields()) {

                if (!field.isAnnotationPresent(javax.persistence.Id.class))
                    continue;

                field.setAccessible(true);
                Object value = field.get(entity);

                if (value == null)
                    continue;

                ID id = (ID) value; // O aviso ainda pode aparecer, mas o código é mais seguro
                this.entityManager.put(id, entity);
            }

            throw new ApplicationException(Errors.ID_NOT_FOUND, null);

        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteById(ID id) {

        T entity = entityManager.get(id);

        if(entity == null)
            return;

        entityManager.remove(id, entity);
    }
}
