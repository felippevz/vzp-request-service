package dev.felippevaz.repositories;

import java.util.List;

/**
 * Contrato comum dos repositórios do framework.
 * <p>
 * A entidade precisa de um campo anotado com {@code @javax.persistence.Id}, e só os
 * campos anotados com {@link dev.felippevaz.annotations.Updatable} são copiados em
 * {@link #update(Object, Object)}.
 *
 * @see ObjectRepository armazenamento em memória
 * @see SqliteRepository armazenamento em SQLite
 */
public interface Repository<T, ID> {

    List<T> findAll();

    /** Entidade com o id informado, ou null se não existir. */
    T findById(ID id);

    boolean existsById(ID id);

    long count();

    /** Insere ou substitui a entidade, usando o valor do campo {@code @Id} como chave. */
    T save(T entity);

    /** Copia apenas os campos {@code @Updatable} para a entidade existente. */
    T update(ID id, T updatedEntity);

    /** Remove a entidade (sem efeito se não existir). */
    void deleteById(ID id);
}
