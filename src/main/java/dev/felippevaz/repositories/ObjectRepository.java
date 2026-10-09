package dev.felippevaz.repositories;

import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repositório em memória ({@link ConcurrentHashMap}). Os dados não sobrevivem a um restart.
 */
public abstract class ObjectRepository<T, ID> implements Repository<T, ID> {

    protected final Map<ID, T> entityManager;

    public ObjectRepository() {
        this.entityManager = new ConcurrentHashMap<>();
    }

    @Override
    public List<T> findAll() {
        return new ArrayList<>(entityManager.values());
    }

    @Override
    public T findById(ID id) {
        return id != null ? entityManager.get(id) : null;
    }

    @Override
    public boolean existsById(ID id) {
        return id != null && entityManager.containsKey(id);
    }

    @Override
    public long count() {
        return entityManager.size();
    }

    @Override
    public T update(ID id, T updatedEntity) {

        T entity = findById(id);

        if(entity == null)
            throw new ApplicationException(Errors.ENTITY_NOT_FOUND, null);

        Entities.copyUpdatableFields(updatedEntity, entity);

        return entity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T save(T entity) {

        ID id = (ID) Entities.extractId(entity);

        this.entityManager.put(id, entity);

        return entity;
    }

    @Override
    public void deleteById(ID id) {

        if (id != null)
            entityManager.remove(id);
    }
}
