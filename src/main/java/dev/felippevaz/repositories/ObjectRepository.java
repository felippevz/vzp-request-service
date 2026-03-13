package dev.felippevaz.repositories;

import org.springframework.beans.BeanUtils;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
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

    public T update(Long id, T updatedEntity) {


        entityManager.getTransaction().begin();

        T entity = entityManager.find(entityClass, id);

        if(entity == null)
            throw new EntityNotFoundException();

        BeanUtils.copyProperties(updatedEntity, entity, "id");

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
