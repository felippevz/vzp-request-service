package dev.felippevaz.repositories;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public abstract class ObjectRepository<T, ID> {

    protected EntityManager entityManager;
    private final Class<T> entityClass;

    @SuppressWarnings("unchecked")
    public ObjectRepository() {
        this.entityManager = PersistenceManagerFactory.getEntityManager();
        this.entityClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public List<T> findAll() {
        String jpql = "SELECT e FROM " + entityClass.getSimpleName() + " e";
        return entityManager.createQuery(jpql, entityClass).getResultList();
    }

    public T findById(ID id) {
        return entityManager.find(entityClass, id);
    }

    public T update(Long id, T updatedEntity, String ignore) {

        entityManager.getTransaction().begin();

        T entity = entityManager.find(entityClass, id);

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

        entityManager.getTransaction().commit();

        return entity;
    }

    public T save(T entity) {

        entityManager.getTransaction().begin();
        entityManager.persist(entity);
        entityManager.getTransaction().commit();

        return entity;
    }

    public void deleteById(ID id) {

        T entity = entityManager.find(entityClass, id);

        if(entity == null)
            return;

        entityManager.getTransaction().begin();
        entityManager.remove(entity);
        entityManager.getTransaction().commit();
    }
}
