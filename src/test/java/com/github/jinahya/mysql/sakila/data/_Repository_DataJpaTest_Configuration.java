package com.github.jinahya.mysql.sakila.data;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.H2Dialect;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * What a {@code @DataJpaTest} needs on top of {@code application.yaml} to run against the embedded H2 of the
 * {@code h2} profile ({@code application-h2.yaml}), which stands in for the MySQL datasource.
 * <p>
 * That profile replaces the {@code DataSource} only. The JPA settings of {@code application.yaml} still apply, and
 * they are written for the installed Sakila database: {@code ddl-auto: none}, which would leave the H2 empty, and
 * {@code MySQLDialect}. This undoes exactly those, and turns off the {@code showSql} that {@code @DataJpaTest} switches
 * on by default.
 * <p>
 * {@link _Repository_DataJpaTest} imports it, so a test extending that class has it already. A slice test that does not
 * extend it pulls it in with {@code @Import(_Repository_DataJpaTest_Configuration.class)}, and activates the profile
 * with {@code @ActiveProfiles("h2")}. A slice test that wants the Sakila database instead leaves both out and adds
 * {@code @AutoConfigureTestDatabase(replace = Replace.NONE)}.
 */
@TestConfiguration(proxyBeanMethods = false)
class _Repository_DataJpaTest_Configuration {

    /**
     * Overrides the MySQL-specific Hibernate properties of {@code application.yaml}, and turns {@code show_sql} off.
     * <p>
     * Boot applies customizers after it has resolved {@code spring.jpa.*}, and lays their result over that, so what
     * this sets wins over {@code application.yaml} and over the {@code showSql} of {@code @DataJpaTest}. A key it
     * removes does not: the original value shows through again, which is why each override here is a {@code put}.
     *
     * @return a customizer for an H2 generated from the entities, with no SQL printed to stdout.
     */
    @Bean
    HibernatePropertiesCustomizer h2HibernatePropertiesCustomizer() {
        return properties -> {
            // The H2 starts empty, so the tables come from the entities. application.yaml's `none` exists to keep
            // hands off the installed Sakila; there is nothing to protect here.
            properties.put(AvailableSettings.HBM2DDL_AUTO, "create-drop");
            // application.yaml names MySQLDialect rather than letting Hibernate detect it; name the right one here.
            properties.put(AvailableSettings.DIALECT, H2Dialect.class.getName());
            // @DataJpaTest defaults showSql to true, which would print every statement to stdout again next to the
            // org.hibernate.SQL logger of application.yaml. Boot hands that flag to the vendor adapter, whose
            // properties only fill keys this map leaves unset, so naming the key here wins.
            properties.put(AvailableSettings.SHOW_SQL, "false");
        };
    }
}
