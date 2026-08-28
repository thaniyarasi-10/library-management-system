package com.kovanlabs.librarymanagement.user.mapping;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class UserMapping {

    public static UserResponse mapToResponse(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponse(
                user.getUuid(),
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }

    public static List<UserResponse> mapToResponse(List<User> users) {
        if (users == null) {
            return Collections.emptyList();
        }
        return users.stream()
                .map(UserMapping::mapToResponse)
                .collect(Collectors.toList());
    }

    public static User mapToEntity(UserRequest request) {
        if (request == null) {
            return null;
        }
        return User.builder()
                .email(request.email())
                .name(request.name())
                .build();
    }
}

