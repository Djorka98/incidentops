package com.djorka.incidentops.dto;

import com.djorka.incidentops.model.UserRole;

public record UserResponse(
    Long id,
    String name,
    String email,
    UserRole role
) {
}
