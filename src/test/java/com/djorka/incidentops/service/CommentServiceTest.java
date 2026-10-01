package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.CommentRequest;
import com.djorka.incidentops.dto.CommentResponse;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.model.Comment;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.IncidentAction;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IncidentHistoryService incidentHistoryService;
    @InjectMocks
    private CommentService commentService;

    private User author;
    private Incident incident;

    @BeforeEach
    void setUp() {
        author = User.builder().id(4L).name("Ana").email("ana@example.com").role(UserRole.RESPONDER).build();
        incident = Incident.builder()
                .id(8L).title("Failure").description("Description")
                .status(IncidentStatus.OPEN).severity(IncidentSeverity.SEV2)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(author.getEmail(), null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsCommentForAuthenticatedUserAndRecordsHistory() {
        when(incidentRepository.findById(8L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(author.getEmail())).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(12L);
            return comment;
        });

        CommentResponse response = commentService.createComment(8L, new CommentRequest("Investigating logs"));

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.authorId()).isEqualTo(4L);
        assertThat(response.incidentId()).isEqualTo(8L);
        assertThat(response.content()).isEqualTo("Investigating logs");
        assertThat(response.createdAt()).isNotNull();
        verify(incidentHistoryService).recordEvent(
                incident, IncidentAction.COMMENT_ADDED, null, "Investigating logs", author);
    }

    @Test
    void rejectsCommentForUnknownIncident() {
        when(incidentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(404L, new CommentRequest("Comment")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Incident not found with id: 404");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void rejectsCommentWhenAuthenticatedUserIsMissingFromDatabase() {
        when(incidentRepository.findById(8L)).thenReturn(Optional.of(incident));
        when(userRepository.findByEmail(author.getEmail())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(8L, new CommentRequest("Comment")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("Authenticated user is no longer available");
    }

    @Test
    void returnsIncidentCommentsInRepositoryOrder() {
        Comment first = comment(1L, "First", LocalDateTime.now().minusMinutes(2));
        Comment second = comment(2L, "Second", LocalDateTime.now().minusMinutes(1));
        when(incidentRepository.existsById(8L)).thenReturn(true);
        when(commentRepository.findByIncidentIdOrderByCreatedAtAsc(8L)).thenReturn(List.of(first, second));

        List<CommentResponse> responses = commentService.getCommentsByIncident(8L);

        assertThat(responses).extracting(CommentResponse::content).containsExactly("First", "Second");
    }

    @Test
    void rejectsCommentListingForUnknownIncident() {
        when(incidentRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.getCommentsByIncident(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Incident not found with id: 404");
        verify(commentRepository, never()).findByIncidentIdOrderByCreatedAtAsc(404L);
    }

    private Comment comment(Long id, String content, LocalDateTime createdAt) {
        return Comment.builder()
                .id(id).content(content).author(author).incident(incident).createdAt(createdAt)
                .build();
    }
}
