package com.djorka.incidentops.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        String content,
        Long authorId,
        String authorName,
        Long incidentId,
        LocalDateTime createdAt
) {
}