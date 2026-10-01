package com.kovanlabs.librarymanagement.user.dto;

import java.io.Serializable;

public record UserResponse(
        String uuid,
        Long id,
        String name,
        String email,
        String role,
        Integer rewardPoints,
        String phone
) implements Serializable {

    public UserResponse(String uuid, Long id, String name, String email, String role, Integer rewardPoints) {
        this(uuid, id, name, email, role, rewardPoints, null);
    }
}
