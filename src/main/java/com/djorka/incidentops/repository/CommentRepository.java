package com.djorka.incidentops.repository;

import com.djorka.incidentops.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = {"author", "incident"})
    List<Comment> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);
}
