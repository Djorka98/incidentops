package com.djorka.incidentops.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incidentops-flyway;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.baseline-on-migrate=false",
        "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWluY2lkZW50b3BzLTMyaA=="
})
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void migratesEmptyDatabaseToVersionOneWithExpectedTables() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");

        Set<String> tables = new TreeSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet resultSet = connection.getMetaData().getTables(
                     null, "PUBLIC", null, new String[]{"TABLE"})) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("TABLE_NAME").toLowerCase());
            }
        }

        assertThat(tables).contains(
                "users",
                "incidents",
                "comments",
                "incident_history",
                "flyway_schema_history"
        );
    }
}
