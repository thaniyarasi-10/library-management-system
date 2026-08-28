package com.kovanlabs.librarymanagement.user.dto;

import java.util.UUID;

public record UserResponse(
    UUID uuid,
    Long id,
    String name,
    String email
) {}
