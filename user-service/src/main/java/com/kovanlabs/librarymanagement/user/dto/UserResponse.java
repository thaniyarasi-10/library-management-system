package com.kovanlabs.librarymanagement.user.dto;

import java.io.Serializable;

public record UserResponse(
        String uuid,
        Long id,
        String name,
        String email,
        String role,
        Integer rewardPoints,
        String phone,
        String status,
        boolean reactivationRequested,
        boolean hasPendingFines,
        Double pendingFineAmount
) implements Serializable {

    public UserResponse(String uuid, Long id, String name, String email, String role, Integer rewardPoints) {
        this(uuid, id, name, email, role, rewardPoints, null, "ACTIVE", false, false, 0.0);
    }

    public UserResponse(String uuid, Long id, String name, String email, String role, Integer rewardPoints, String phone) {
        this(uuid, id, name, email, role, rewardPoints, phone, "ACTIVE", false, false, 0.0);
    }

    public UserResponse(String uuid, Long id, String name, String email, String role, Integer rewardPoints, String phone, String status) {
        this(uuid, id, name, email, role, rewardPoints, phone, status, false, false, 0.0);
    }

}
