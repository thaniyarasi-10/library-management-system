package com.kovanlabs.librarymanagement.user.dto;

import java.io.Serializable;
import java.util.UUID;

public record UserResponse(
    UUID uuid,
    Long id,
    String name,
    String email
) implements Serializable {}
