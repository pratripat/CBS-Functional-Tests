package com.banking.tests.util;

import com.banking.testframework.container.GenericInfraComponent;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Pre-configured Postgres component for banking platform functional tests.
 *
 * Exposes SPRING_DATASOURCE_* env vars via getExposedEnvironment() so that
 * dependent TomcatServiceComponents receive them automatically through the
 * framework's accumulated env propagation in ContainerRegistry.
 *
 * The JDBC URL uses the Docker network alias "postgres" as the hostname — this
 * is resolvable by other containers on the same Docker bridge network.
 */
public final class PostgresComponent {

    private PostgresComponent() {
    }

    public static final String ALIAS = "postgres";
    public static final String DB_NAME = "banking_test";
    public static final String DB_USER = "testuser";
    public static final String DB_PASS = "testpass";

    public static GenericInfraComponent create() {
        return GenericInfraComponent.builder(ALIAS, "postgres:15-alpine")
                .port(5432)
                .componentType("postgres")
                .env("POSTGRES_DB", DB_NAME)
                .env("POSTGRES_USER", DB_USER)
                .env("POSTGRES_PASSWORD", DB_PASS)
                .waitStrategy(Wait.forLogMessage(
                        ".*database system is ready to accept connections.*", 2))
                // These env vars are injected into every dependent container.
                // The URL uses "postgres" (Docker network alias) as hostname.
                .exposeEnv("SPRING_DATASOURCE_URL",
                        c -> "jdbc:postgresql://" + ALIAS + ":5432/" + DB_NAME)
                .exposeEnv("SPRING_DATASOURCE_USERNAME", c -> DB_USER)
                .exposeEnv("SPRING_DATASOURCE_PASSWORD", c -> DB_PASS)
                .exposeEnv("SPRING_JPA_HIBERNATE_DDL_AUTO", c -> "update")
                .build();
    }
}
