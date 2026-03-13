package dev.felippevaz.repositories;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class PersistenceManagerFactory {

    private static EntityManagerFactory entityManagerFactory;

    static {
        init(defaultConfig());
    }

    public static void init(Map<String, String> properties) {
        entityManagerFactory = Persistence.createEntityManagerFactory("default", properties);
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
