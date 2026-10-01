package com.djorka.incidentops.service;

import com.djorka.incidentops.dto.CommentRequest;
import com.djorka.incidentops.dto.CommentResponse;
import com.djorka.incidentops.exception.ResourceNotFoundException;
import com.djorka.incidentops.model.Comment;
import com.djorka.incidentops.model.Incident;
import com.djorka.incidentops.model.User;
import com.djorka.incidentops.repository.CommentRepository;
import com.djorka.incidentops.repository.IncidentRepository;
import com.djorka.incidentops.repository.UserRepository;
import com.djorka.incidentops.model.IncidentAction;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

        private final CommentRepository commentRepository;
        private final IncidentRepository incidentRepository;
        private final UserRepository userRepository;
        private final IncidentHistoryService incidentHistoryService;

        private CommentResponse toResponse(Comment comment) {
                return new CommentResponse(
                                comment.getId(),
                                comment.getContent(),
                                comment.getAuthor().getId(),
                                comment.getAuthor().getName(),
                                comment.getIncident().getId(),
                                comment.getCreatedAt());
        }

        private User getAuthenticatedUser() {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                String email = authentication.getName();

                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                                                "Authenticated user is no longer available"));
        }

        @Transactional
        public CommentResponse createComment(Long incidentId, CommentRequest request) {
                Incident incident = incidentRepository.findById(incidentId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Incident not found with id: " + incidentId));

                User author = getAuthenticatedUser();

                Comment comment = Comment.builder()
                                .content(request.content())
                                .author(author)
                                .incident(incident)
                                .createdAt(LocalDateTime.now())
                                .build();

                Comment savedComment = commentRepository.save(comment);

                incidentHistoryService.recordEvent(
                                incident,
                                IncidentAction.COMMENT_ADDED,
                                null,
                                savedComment.getContent(),
                                author);

                return toResponse(savedComment);
        }

        @Transactional(readOnly = true)
        public List<CommentResponse> getCommentsByIncident(Long incidentId) {
                if (!incidentRepository.existsById(incidentId)) {
                        throw new ResourceNotFoundException(
                                        "Incident not found with id: " + incidentId);
                }

                return commentRepository
                                .findByIncidentIdOrderByCreatedAtAsc(incidentId)
                                .stream()
                                .map(this::toResponse)
                                .toList();
        }
}
