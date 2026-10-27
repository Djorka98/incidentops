package com.djorka.incidentops.service;

import com.djorka.incidentops.exception.ResourceConflictException;
import com.djorka.incidentops.model.Comment;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentAction;
import com.djorka.incidentops.model.IncidentHistory;
import com.djorka.incidentops.model.IncidentSeverity;
import com.djorka.incidentops.model.IncidentStatus;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.model.UserRole;
import com.djorka.incidentops.repository.CommentRepository;
import com.djorka.incidentops.repository.IncidentHistoryRepository;
import com.djorka.incidentops.repository.IncidentRepository;
import com.djorka.incidentops.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import(UserService.class)
class UserDeletionIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IncidentRepository incidentRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private IncidentHistoryRepository incidentHistoryRepository;

    @Test
    void deletesUnreferencedUserFromDatabase() {
        User admin = saveUser("Admin", "admin@example.com", UserRole.ADMIN);
        User freeUser = saveUser("Viewer", "viewer@example.com", UserRole.VIEWER);

        userService.deleteUser(freeUser.getId(), admin.getId());

        assertThat(userRepository.findById(freeUser.getId())).isEmpty();
        assertThat(userRepository.findById(admin.getId())).isPresent();
    }

    @Test
    void preservesUserAndAllOperationalDataWhenReferenced() {
        User admin = saveUser("Admin", "admin@example.com", UserRole.ADMIN);
        User operator = saveUser("Operator", "operator@example.com", UserRole.RESPONDER);
        LocalDateTime now = LocalDateTime.now();
        Incident incident = incidentRepository.save(Incident.builder()
                .title("Database outage")
                .description("Primary database unavailable")
                .status(IncidentStatus.OPEN)
                .severity(IncidentSeverity.SEV1)
                .createdAt(now)
                .updatedAt(now)
                .assignedTo(operator)
                .build());
        commentRepository.save(Comment.builder()
                .content("Investigating")
                .author(operator)
                .incident(incident)
                .createdAt(now)
                .build());
        incidentHistoryRepository.save(IncidentHistory.builder()
                .incident(incident)
                .action(IncidentAction.ASSIGNED)
                .newValue(operator.getName())
                .createdAt(now)
                .performedBy(operator)
                .build());

        assertThatThrownBy(() -> userService.deleteUser(operator.getId(), admin.getId()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessage("User cannot be deleted because it is referenced by operational data");

        assertThat(userRepository.findById(operator.getId())).isPresent();
        assertThat(incidentRepository.count()).isEqualTo(1);
        assertThat(commentRepository.count()).isEqualTo(1);
        assertThat(incidentHistoryRepository.count()).isEqualTo(1);
    }

    private User saveUser(String name, String email, UserRole role) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .role(role)
                .build());
    }
}
