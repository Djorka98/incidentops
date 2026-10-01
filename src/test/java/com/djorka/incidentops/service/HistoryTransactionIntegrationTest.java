package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.CommentRequest;
import com.djorka.incidentops.dto.IncidentRequest;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.CommentRepository;
import com.djorka.incidentops.repository.IncidentRepository;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:incidentops-transactions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWluY2lkZW50b3BzLTMyaA=="
})
class HistoryTransactionIntegrationTest {

    @Autowired
    private IncidentService incidentService;
    @Autowired
    private CommentService commentService;
    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private IncidentHistoryService incidentHistoryService;

    private User authenticatedUser;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        incidentRepository.deleteAll();
        userRepository.deleteAll();

        authenticatedUser = userRepository.save(User.builder()
                .name("Operator")
                .email("operator@example.com")
                .role(UserRole.RESPONDER)
                .password("encoded")
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser.getEmail(), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rollsBackIncidentCreationWhenHistoryFails() {
        doThrow(new IllegalStateException("history unavailable"))
                .when(incidentHistoryService)
                .recordEvent(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> incidentService.createIncident(
                new IncidentRequest("API down", "Unavailable", IncidentSeverity.SEV1)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(incidentRepository.count()).isZero();
    }

    @Test
    void rollsBackCommentCreationWhenHistoryFails() {
        Incident incident = incidentRepository.save(Incident.builder()
                .title("API down")
                .description("Unavailable")
                .status(IncidentStatus.OPEN)
                .severity(IncidentSeverity.SEV1)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        doThrow(new IllegalStateException("history unavailable"))
                .when(incidentHistoryService)
                .recordEvent(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> commentService.createComment(
                incident.getId(), new CommentRequest("Investigating")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(commentRepository.count()).isZero();
    }
}
