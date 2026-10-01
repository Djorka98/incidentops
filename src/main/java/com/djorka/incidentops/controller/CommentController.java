package com.djorka.incidentops.controller;

import com.djorka.incidentops.dto.CommentRequest;
import com.djorka.incidentops.dto.CommentResponse;
import com.djorka.incidentops.service.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/api/incidents/{incidentId}/comments")
@RequiredArgsConstructor
@Validated
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable @Positive Long incidentId,
            @Valid @RequestBody CommentRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(commentService.createComment(incidentId, request));
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getCommentsByIncident(
            @PathVariable @Positive Long incidentId
    ) {
        return ResponseEntity.ok(
                commentService.getCommentsByIncident(incidentId)
        );
    }
}
