/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.persistence;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public interface RegisterDatabaseProperties {

    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        // Be aware that calling testContainer.getJdbcUrl returns a URL that starts
        // something like "jdbc:postgresql://localhost...etc...".
        // The URL needs to start with "jdbc:tc:postgresql", The 'tc' between 'jdbc' and
        // 'postgres' is the hint to the driver that the TestContainer handles the URL,
        // not the typical runtime JDCB driver.
        registry.add("spring.datasource.url", () -> "jdbc:tc:postgresql:15.1-alpine:///public");
        registry.add("spring.datasource.driver-class-name", () -> "org.testcontainers.jdbc.ContainerDatabaseDriver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "true");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "true");
    }
}
