package com.kovanlabs.librarymanagement.user.mapping;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for converting between {@link User} entities and User DTOs.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Maps a {@link User} entity to a {@link UserResponse} DTO.
     *
     * @param user The user entity
     * @return The mapped {@link UserResponse} DTO
     */
    @Mapping(target = "rewardPoints", ignore = true)
    UserResponse mapToResponse(User user);

    /**
     * Maps a list of {@link User} entities to a list of {@link UserResponse} DTOs.
     *
     * @param users The list of user entities
     * @return List of mapped {@link UserResponse} DTOs
     */
    List<UserResponse> mapToResponse(List<User> users);

    /**
     * Maps a {@link UserRequest} DTO to a {@link User} entity.
     *
     * @param request The user request DTO
     * @return The unpersisted {@link User} entity
     */
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User mapToEntity(UserRequest request);
}
