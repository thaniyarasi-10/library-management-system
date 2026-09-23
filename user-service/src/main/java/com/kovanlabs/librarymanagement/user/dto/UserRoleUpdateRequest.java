package com.kovanlabs.librarymanagement.user.dto;

import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record UserRoleUpdateRequest(
        @NotNull(message = "Role is required")
        RoleEnum role
) implements Serializable {}
