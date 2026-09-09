/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.inventory.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.devtools.restart.RestartScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfiguration {

    @Bean
    @ServiceConnection
    @RestartScope
    public PostgreSQLContainer postgreSQLContainer() {
        return new PostgreSQLContainer("postgres")
                .withReuse(true)
                .withStartupTimeout(Duration.ofMinutes(1))
                .withInitScript("create-schema.sql")
                .waitingFor(Wait.forListeningPort());
    }
}
