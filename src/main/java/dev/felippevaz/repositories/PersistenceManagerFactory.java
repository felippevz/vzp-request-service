package dev.felippevaz.repositories;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;

public class PersistenceManagerFactory {

    private static EntityManagerFactory entityManagerFactory;

    static {
        init(defaultConfig());
    }

    public static void init(Map<String, String> properties) {

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(properties)
                .build();

        MetadataSources metadataSources = new MetadataSources(registry);

        try (ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            for (ClassInfo classInfo :
                    scanResult.getClassesWithAnnotation(Entity.class.getName())) {

                Class<?> entityClass = classInfo.loadClass();
                metadataSources.addAnnotatedClass(entityClass);
            }
        }

        try (SessionFactory sessionFactory = metadataSources.buildMetadata().buildSessionFactory()) {

            entityManagerFactory = sessionFactory.unwrap(EntityManagerFactory.class);
        }
    }

    private static Map<String, String> defaultConfig() {
        Map<String, String> props = new HashMap<>();

        props.put("javax.persistence.jdbc.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        props.put("javax.persistence.jdbc.driver", "org.h2.Driver");
        props.put("javax.persistence.jdbc.user", "sa");
        props.put("javax.persistence.jdbc.password", "");

        props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        props.put("hibernate.hbm2ddl.auto", "update");

        return props;
    }

    public static EntityManager getEntityManager() {
        return entityManagerFactory.createEntityManager();
    }
}
