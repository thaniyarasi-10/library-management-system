package com.kovanlabs.librarymanagement.user.mapping;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.SObjectAttributes;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * MapStruct mapper for converting between {@link User} entities and User DTOs.
 */
@Mapper(imports = {SObjectAttributes.class, SObject.class, UUID.class, Objects.class, AccountStatus.class})
public interface UserMapper {

    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    /**
     * Maps a {@link User} entity to a {@link UserResponse} DTO.
     *
     * @param user The user entity
     * @return The mapped {@link UserResponse} DTO
     */
    @Mapping(target = "rewardPoints", ignore = true)
    @Mapping(target = "status", expression = "java(Objects.nonNull(user.getStatus()) ? user.getStatus().name() : \"ACTIVE\")")
    @Mapping(target = "reactivationRequested", expression = "java(Objects.nonNull(user) && user.isReactivationRequested())")
    @Mapping(target = "reactivationRequestStatus", expression = "java((Objects.nonNull(user) && Objects.nonNull(user.getReactivationRequestStatus())) ? user.getReactivationRequestStatus().name() : null)")
    @Mapping(target = "hasPendingFines", constant = "false")
    @Mapping(target = "pendingFineAmount", expression = "java(0.0)")
    UserResponse mapToResponse(User user);

    /**
     * Internal MapStruct mapping combining User and reward points.
     */
    @Mapping(target = "uuid", source = "user.uuid")
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "role", expression = "java((Objects.nonNull(user) && Objects.nonNull(user.getRole())) ? user.getRole().name() : null)")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "rewardPoints", source = "rewardPoints")
    @Mapping(target = "status", expression = "java((Objects.nonNull(user) && Objects.nonNull(user.getStatus())) ? user.getStatus().name() : \"ACTIVE\")")
    @Mapping(target = "reactivationRequested", expression = "java(Objects.nonNull(user) && user.isReactivationRequested())")
    @Mapping(target = "reactivationRequestStatus", expression = "java((Objects.nonNull(user) && Objects.nonNull(user.getReactivationRequestStatus())) ? user.getReactivationRequestStatus().name() : null)")
    @Mapping(target = "hasPendingFines", constant = "false")
    @Mapping(target = "pendingFineAmount", expression = "java(0.0)")
    UserResponse mapUserAndReward(User user, Integer rewardPoints);

    /**
     * Maps a {@link User} entity and calculated reward points to a {@link UserResponse} DTO.
     *
     * @param user         The user entity
     * @param rewardPoints The calculated reward points
     * @return The populated {@link UserResponse} DTO
     */
    default UserResponse mapToResponse(User user, Integer rewardPoints) {
        if (Objects.isNull(user)) {
            return null;
        }
        return mapUserAndReward(user, rewardPoints);
    }

    @Mapping(target = "uuid", source = "user.uuid")
    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "role", expression = "java((Objects.nonNull(user) && Objects.nonNull(user.getRole())) ? user.getRole().name() : null)")
    @Mapping(target = "phone", source = "user.phone")
    @Mapping(target = "rewardPoints", source = "rewardPoints")
    @Mapping(target = "status", expression = "java((Objects.nonNull(user) && Objects.nonNull(user.getStatus())) ? user.getStatus().name() : \"ACTIVE\")")
    @Mapping(target = "reactivationRequested", expression = "java(Objects.nonNull(user) && user.isReactivationRequested())")
    @Mapping(target = "reactivationRequestStatus", expression = "java((Objects.nonNull(user) && Objects.nonNull(user.getReactivationRequestStatus())) ? user.getReactivationRequestStatus().name() : null)")
    @Mapping(target = "hasPendingFines", source = "hasPendingFines")
    @Mapping(target = "pendingFineAmount", source = "pendingFineAmount")
    UserResponse mapUserWithFines(User user, Integer rewardPoints, boolean hasPendingFines, Double pendingFineAmount);

    default UserResponse mapToResponse(User user, Integer rewardPoints, boolean hasPendingFines, Double pendingFineAmount) {
        if (Objects.isNull(user)) {
            return null;
        }
        return mapUserWithFines(user, rewardPoints, hasPendingFines, pendingFineAmount);
    }

    /**
     * Maps a list of {@link User} entities to a list of {@link UserResponse} DTOs.
     *
     * @param users The list of user entities
     * @return List of mapped {@link UserResponse} DTO
     */
    List<UserResponse> mapToResponse(List<User> users);

    /**
     * Maps a list of {@link User} entities and their pre-fetched reward points map to a list of {@link UserResponse} DTOs.
     *
     * @param users     List of user entities
     * @param rewardMap Pre-fetched map of user UUID to reward points
     * @return List of mapped {@link UserResponse} DTOs
     */
    default List<UserResponse> mapToResponse(List<User> users, Map<String, Integer> rewardMap) {
        if (Objects.isNull(users) || users.isEmpty()) {
            return List.of();
        }
        return users.stream().map(user -> {
            if (Objects.isNull(user)) {
                return null;
            }
            int points = (Objects.nonNull(user.getUuid()) && Objects.nonNull(rewardMap))
                    ? rewardMap.getOrDefault(user.getUuid(), 0)
                    : 0;
            return mapToResponse(user, points);
        }).toList();
    }

    /**
     * Maps a {@link UserRequest} DTO to a {@link User} entity.
     *
     * @param request The user request DTO
     * @return The unpersisted {@link User} entity
     */
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "inactivityWarningSentAt", ignore = true)
    @Mapping(target = "reactivationRequestStatus", ignore = true)
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
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "reactivationRequestStatus", source = "reactivationRequestStatus")
    @Mapping(target = "reward", source = "rewardPoints")
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "inactivityWarningSentAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "errors", ignore = true)
    ContactSObject toContactSObject(UserResponse user);

    /**
     * Converts a {@link User} entity to a {@link ContactSObject}.
     * When user status is DEACTIVATED, reward is explicitly set to 0.
     */
    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.CONTACT.getObjectName()).build())")
    @Mapping(target = "externalUserUuid", source = "uuid")
    @Mapping(target = "legacyUserId", source = "id")
    @Mapping(target = "lastName", expression = "java((Objects.nonNull(user.getName()) && !user.getName().isBlank()) ? user.getName() : \"User\")")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "role", expression = "java(Objects.nonNull(user.getRole()) ? user.getRole().name() : null)")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "status", expression = "java(Objects.nonNull(user.getStatus()) ? user.getStatus().name() : null)")
    @Mapping(target = "lastLoginAt", expression = "java(Objects.nonNull(user.getLastLoginAt()) ? user.getLastLoginAt().toString() : null)")
    @Mapping(target = "reactivationRequestStatus", expression = "java(Objects.nonNull(user.getReactivationRequestStatus()) ? user.getReactivationRequestStatus().name() : null)")
    @Mapping(target = "inactivityWarningSentAt", expression = "java(Objects.nonNull(user.getInactivityWarningSentAt()) ? user.getInactivityWarningSentAt().toString() : null)")
    @Mapping(target = "reward", expression = "java(AccountStatus.DEACTIVATED.equals(user.getStatus()) ? Integer.valueOf(0) : null)")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "errors", ignore = true)
    ContactSObject toContactSObject(User user);

    /**
     * Converts a {@link ContactSObject} to a {@link UserResponse} DTO.
     */
    @Mapping(target = "uuid", source = "externalUserUuid")
    @Mapping(target = "id", source = "legacyUserId")
    @Mapping(target = "name", source = "lastName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "rewardPoints", expression = "java(Objects.nonNull(contact.getReward()) ? contact.getReward() : 0)")
    @Mapping(target = "status", expression = "java((Objects.nonNull(contact.getStatus()) && !contact.getStatus().isBlank()) ? contact.getStatus() : \"ACTIVE\")")
    @Mapping(target = "reactivationRequestStatus", source = "reactivationRequestStatus")
    @Mapping(target = "reactivationRequested", expression = "java(\"PENDING\".equalsIgnoreCase(contact.getReactivationRequestStatus()))")
    @Mapping(target = "hasPendingFines", constant = "false")
    @Mapping(target = "pendingFineAmount", expression = "java(0.0)")
    UserResponse toUserResponse(ContactSObject contact);

    /**
     * Converts a list of {@link ContactSObject} models to a list of {@link UserResponse} DTOs.
     */
    List<UserResponse> toUserResponseList(List<ContactSObject> contacts);
}
