package com.kovanlabs.librarymanagement.user.mapping;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.SObjectAttributes;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MapStruct mapper for converting between {@link User} entities and User DTOs.
 */
@Mapper(imports = {SObjectAttributes.class, SObject.class, UUID.class, Objects.class})
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

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
     * @return List of mapped {@link UserResponse} DTO
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
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "salesforceSyncStatus", ignore = true)
    @Mapping(target = "salesforceRetryCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User mapToEntity(UserRequest request);

    // --- Salesforce Contact Mappings ---

    /**
     * Converts a {@link UserResponse} DTO to a {@link ContactSObject}.
     */
    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.CONTACT.getObjectName()).build())")
    @Mapping(target = "externalUserUuid", source = "uuid")
    @Mapping(target = "legacyUserId", source = "id")
    @Mapping(target = "lastName", expression = "java((Objects.nonNull(user.name()) && !user.name().isBlank()) ? user.name() : \"User\")")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "errors", ignore = true)
    ContactSObject toContactSObject(UserResponse user);

    /**
     * Converts a {@link User} entity to a {@link ContactSObject}.
     */
    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.CONTACT.getObjectName()).build())")
    @Mapping(target = "externalUserUuid", source = "uuid")
    @Mapping(target = "legacyUserId", source = "id")
    @Mapping(target = "lastName", expression = "java((Objects.nonNull(user.getName()) && !user.getName().isBlank()) ? user.getName() : \"User\")")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "role", expression = "java(Objects.nonNull(user.getRole()) ? user.getRole().name() : null)")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "errors", ignore = true)
    ContactSObject toContactSObject(User user);

    /**
     * Converts a {@link ContactSObject} to a {@link UserResponse} DTO.
     */
    @Mapping(target = "uuid", expression = "java(Objects.nonNull(contact.getExternalUserUuid()) ? UUID.fromString(contact.getExternalUserUuid()) : null)")
    @Mapping(target = "id", source = "legacyUserId")
    @Mapping(target = "name", source = "lastName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "rewardPoints", expression = "java(0)")
    UserResponse toUserResponse(ContactSObject contact);

    /**
     * Converts a list of {@link ContactSObject} models to a list of {@link UserResponse} DTOs.
     */
    List<UserResponse> toUserResponseList(List<ContactSObject> contacts);
}
