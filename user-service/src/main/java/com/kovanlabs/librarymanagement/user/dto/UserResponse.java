package com.kovanlabs.librarymanagement.user.dto;

import java.io.Serializable;

public record UserResponse(
        String uuid,
        Long id,
        String name,
        String email,
        String role,
        Integer rewardPoints
) implements Serializable {

}
