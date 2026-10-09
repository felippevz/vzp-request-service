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

/**
 * Reflection compartilhada pelos repositórios: localizar o {@code @Id} e copiar os
 * campos {@code @Updatable}. Os campos de cada classe são resolvidos uma vez e
 * guardados em cache.
 */
final class Entities {

    private static final Logger LOGGER = Logger.getLogger(Entities.class.getName());

    private static final Map<Class<?>, Field> ID_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Field>> UPDATABLE_FIELDS = new ConcurrentHashMap<>();

    private Entities() {
    }

    /**
     * @return o valor do campo {@code @Id}.
     * @throws ApplicationException {@link Errors#ID_NOT_FOUND} se não houver campo
     *                              {@code @Id} ou se o valor for null.
     */
    static Object extractId(Object entity) {

        Field field = idField(entity.getClass());

        if (field == null)
            throw new ApplicationException(Errors.ID_NOT_FOUND, null);

        try {

            Object value = field.get(entity);

            if (value == null)
                throw new ApplicationException(Errors.ID_NOT_FOUND, null);

            return value;

        } catch (IllegalAccessException exception) {
            LOGGER.log(Level.SEVERE, "Failed to read @Id of " + entity.getClass().getSimpleName(), exception);
            throw new ApplicationException(Errors.FIELD_COPY_ERROR, exception);
        }
    }

    // Apenas campos anotados com @Updatable são copiados do payload do cliente para
    // a entidade persistida. Isto evita "mass assignment": sem allow-list explícita,
    // qualquer campo do objeto (ids, flags internas, etc.) poderia ser sobrescrito
    // por um payload malicioso.
    static <T> void copyUpdatableFields(T source, T target) {

        Class<?> type = target.getClass();
        List<Field> fields = updatableFields(type);

        if (fields.isEmpty()) {
            LOGGER.warning(() -> "update() called on " + type.getSimpleName()
                    + " but no field is annotated with @Updatable - nothing was changed");
            return;
        }

        for (Field field : fields) {
            try {
                field.set(target, field.get(source));
            } catch (IllegalAccessException | IllegalArgumentException exception) {
                LOGGER.log(Level.SEVERE, "Failed to copy field '" + field.getName()
                        + "' during update of " + type.getSimpleName(), exception);
                throw new ApplicationException(Errors.FIELD_COPY_ERROR, exception);
            }
        }
    }

    private static Field idField(Class<?> type) {

        Field cached = ID_FIELDS.get(type);
        if (cached != null)
            return cached;

        for (Field field : allFields(type)) {
            if (field.isAnnotationPresent(javax.persistence.Id.class)) {
                field.setAccessible(true);
                ID_FIELDS.put(type, field);
                return field;
            }
        }

        return null;
    }

    private static List<Field> updatableFields(Class<?> type) {

        List<Field> cached = UPDATABLE_FIELDS.get(type);
        if (cached != null)
            return cached;

        List<Field> fields = new ArrayList<>();

        for (Field field : allFields(type)) {
            if (field.isAnnotationPresent(Updatable.class)) {
                field.setAccessible(true);
                fields.add(field);
            }
        }

        UPDATABLE_FIELDS.put(type, fields);
        return fields;
    }

    // Campos da classe e das superclasses (exceto estáticos), para que entidades
    // com herança funcionem.
    private static List<Field> allFields(Class<?> type) {

        List<Field> fields = new ArrayList<>();

        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass())
            for (Field field : current.getDeclaredFields())
                if (!Modifier.isStatic(field.getModifiers()))
                    fields.add(field);

        return fields;
    }
}
