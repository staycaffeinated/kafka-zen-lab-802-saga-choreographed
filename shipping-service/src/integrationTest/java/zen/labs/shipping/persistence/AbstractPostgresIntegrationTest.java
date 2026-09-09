/*
 * Copyright 2026 [CopyrightOwner]
 */
package zen.labs.shipping.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import javax.script.ScriptException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {"auto.create.topics.enable=true"})
@ContextConfiguration(initializers = AbstractPostgresIntegrationTest.Initializer.class)
@SuppressWarnings({"java:S2187" // this isn't a Test class so it won't contain tests
})
@Slf4j
public class AbstractPostgresIntegrationTest {

    // Where Metacode writes db scripts when neither Liquibase nor Flyway are used
    // (although Flyway is not supported yet).
    private static final String SCHEMA_FOLDER = "db/migration/";

    public static PostgreSQLContainer postgreSQLContainer() {
        return Initializer.postgres;
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17").withReuse(true);

        private static void startContainers() {
            /*
             * Other containers like RabbitMQ or other databases and such
             * can also be added to this deepStart call.
             */
            Startables.deepStart(Stream.of(postgres)).join();
        }

        private static Map<String, Object> createConnectionConfiguration() {
            return Map.of(
                    "spring.datasource.url", postgres.getJdbcUrl(),
                    "spring.datasource.username", postgres.getUsername(),
                    "spring.datasource.password", postgres.getPassword(),
                    "spring.jpa.show-sql", "true",
                    "spring.jpa.properties.hibernate.format_sql", "true");
        }

        @Override
        public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
            startContainers();
            initTestContainerDatabase();
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            MapPropertySource testcontainers = new MapPropertySource("testcontainers", createConnectionConfiguration());
            environment.getPropertySources().addFirst(testcontainers);
        }
    }

    /*
     * Initialize the database within the TestContainer database.
     * When using tools like Liquibase or Flyway, creating the database artifacts is done for you.
     * Without those tools, you usually have to depend on Hibernate or manually set up the database yourself.
     * When a database schema is _not_ used, Hibernate may be sufficient.
     * However, if you are using a database schema, Hibernate will not create the schema for you,
     * nor the artifacts within the schema (such as tables, indexes, sequences). Those artifacts
     * have to be created by you. This is where this method helps.
     *
     * See: https://stackoverflow.com/questions/53078306/populate-a-database-with-testcontainers-in-a-springboot-integration-test
     */
    public static void initTestContainerDatabase() {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        final String pattern = "classpath:" + SCHEMA_FOLDER + "*.sql";

        try {
            Resource[] scripts = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                    .getResources(pattern);

            var containerDelegate = new JdbcDatabaseDelegate(postgreSQLContainer(), "");
            String parentPath =
                    scripts.length > 0 ? scripts[0].getFile().getParentFile().getAbsolutePath() : "";
            Arrays.stream(scripts).forEach(script -> {
                try {
                    ScriptUtils.executeDatabaseScript(containerDelegate, parentPath, loadScript(script));
                } catch (ScriptException | IOException e) {
                    log.error(
                            "An error occurred while either loading or executing this database script: {}",
                            SCHEMA_FOLDER + script.getFilename(),
                            e);
                }
            });
        } catch (IOException e) {
            log.info("No database scripts were executed from the {} folder because: {}", SCHEMA_FOLDER, e.getMessage());
            log.info(
                    "This is only problematic when the database requires a schema AND neither Liquibase nor Flyway are used.");
            log.info(
                    "Hibernate does not auto-create schemas so a database script that creates the schema is expected in the {} folder.",
                    SCHEMA_FOLDER);
            log.info("If the database does not require a schema, these messages can be ignored.");
        }
    }

    private static String loadScript(Resource script) throws IOException {
        return FileUtils.readFileToString(script.getFile(), StandardCharsets.UTF_8);
    }
}
