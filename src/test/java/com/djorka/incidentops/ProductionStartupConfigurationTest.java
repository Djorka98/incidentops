package com.djorka.incidentops;

import com.djorka.incidentops.repository.UserRepository;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incidentops-prod;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWluY2lkZW50b3BzLTMyaA=="
})
@AutoConfigureMockMvc
class ProductionStartupConfigurationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void usesServerlessProductionStartupSettings() {
        assertThat(environment.getProperty("spring.main.lazy-initialization", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.data.jpa.repositories.bootstrap-mode")).isEqualTo("lazy");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.datasource.hikari.minimum-idle", Integer.class)).isZero();
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class)).isEqualTo(4);
        assertThat(environment.getProperty("spring.datasource.hikari.initialization-fail-timeout", Long.class)).isEqualTo(-1L);
        assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout", Long.class)).isEqualTo(10_000L);
    }

    @Test
    void keepsLandingPublicAndProtectedRoutesSecured() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void initializesRepositoriesOnUseAfterFlywayMigration() {
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void doesNotExposeSpringDocInProduction() throws Exception {
        assertThat(applicationContext.getBeansOfType(OpenAPI.class)).isEmpty();

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }
}
