package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final RewardRepository rewardRepository;
    private final SalesforceSync salesforceSyncDelegate;

    /**
     * Helper method to map a single User entity to {@link UserResponse} along with
     * their calculated reward points.
     *
     * @param user The user entity
     * @return The populated {@link UserResponse}
     */
    private UserResponse mapToUserResponseWithRewards(User user) {
        if (user == null)
            return null;
        Integer points = 0;
        if (user.getUuid() != null) {
            points = rewardRepository.findByUserUuid(user.getUuid())
                    .map(Reward::getPoints)
                    .orElse(0);
        }
        return new UserResponse(
                user.getUuid(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                points);
    }

    /**
     * Helper method to batch map a list of User entities to {@link UserResponse}
     * DTOs with reward points.
     *
     * @param users List of user entities
     * @return List of mapped {@link UserResponse} DTOs
     */
    private List<UserResponse> mapToUserResponseListWithRewards(List<User> users) {
        if (users == null || users.isEmpty())
            return List.of();
        List<UUID> uuids = users.stream()
                .map(User::getUuid)
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, Integer> rewardMap = rewardRepository.findByUserUuidIn(uuids).stream()
                .collect(Collectors.toMap(
                        Reward::getUserUuid,
                        Reward::getPoints,
                        (p1, p2) -> p1));

        return users.stream().map(user -> new UserResponse(
                user.getUuid(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getUuid() != null ? rewardMap.getOrDefault(user.getUuid(), 0) : 0)).toList();
    }

    /**
     * Creates a new user, saves to MySQL, and triggers dual-write sync with
     * Salesforce if configured.
     *
     * @param request The user creation request payload
     * @return The created {@link UserResponse} DTO
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public UserResponse createUser(UserRequest request) {
        User user = UserMapper.INSTANCE.mapToEntity(request);
        if (user != null) {
            user.setRole(RoleEnum.USER);
        }
        User savedUser = userRepository.save(user);
        UserResponse response = mapToUserResponseWithRewards(savedUser);

        if (salesforceSyncDelegate != null) {
            try {
                salesforceSyncDelegate.syncContact(UserMapper.INSTANCE.toContactSObject(savedUser));
                savedUser.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                userRepository.save(savedUser);
            } catch (Exception e) {
                int retryCount = savedUser.getSalesforceRetryCount() + 1;
                savedUser.setSalesforceRetryCount(retryCount);
                savedUser.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                userRepository.save(savedUser);
                log.error("Salesforce dual-write failed for user creation [User ID: {}, UUID: {}, Operation: CREATE, RetryCount: {}]: {}",
                        savedUser.getId(), savedUser.getUuid(), retryCount, e.getMessage());
            }
        }

        return response;
    }

    /**
     * Fetches all users from Salesforce if delegate is active and records exist,
     * otherwise falls back to MySQL.
     *
     * @return List of {@link UserResponse} DTOs
     */
    @Override
    public List<UserResponse> getAllUsers() {
        if (salesforceSyncDelegate != null) {
            try {
                var sfContacts = salesforceSyncDelegate.fetchContactsFromSalesforce();
                if (sfContacts != null && !sfContacts.isEmpty()) {
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} users from Salesforce SOQL",
                            sfContacts.size());
                    return UserMapper.INSTANCE.toUserResponseList(sfContacts);
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for users, falling back to MySQL: {}",
                        e.getMessage());
            }
        }
        log.info("[DATA SOURCE: MYSQL] Fetching users from MySQL database");
        return UserMapper.INSTANCE.mapToResponse(userRepository.findAll());
    }

    /**
     * Fetches a paginated list of users from Salesforce (with in-memory paging) or
     * MySQL database.
     *
     * @param page    Zero-based page number
     * @param size    Number of items per page
     * @param sortBy  Field to sort by
     * @param sortDir Sort direction ("asc" or "desc")
     * @return {@link PagedResponse} of {@link UserResponse}
     */
    @Override
    public PagedResponse<UserResponse> getAllUsers(int page, int size, String sortBy, String sortDir) {
        if (salesforceSyncDelegate != null) {
            try {
                var sfContacts = salesforceSyncDelegate.fetchContactsFromSalesforce();
                if (sfContacts != null && !sfContacts.isEmpty()) {
                    List<UserResponse> sfUsers = UserMapper.INSTANCE.toUserResponseList(sfContacts);
                    log.info(
                            "[DATA SOURCE: SALESFORCE] Successfully fetched {} users from Salesforce SOQL (paging in memory)",
                            sfUsers.size());
                    int fromIndex = Math.min(page * size, sfUsers.size());
                    int toIndex = Math.min(fromIndex + size, sfUsers.size());
                    List<UserResponse> pageContent = sfUsers.subList(fromIndex, toIndex);
                    int totalPages = (int) Math.ceil((double) sfUsers.size() / size);
                    return new PagedResponse<>(
                            pageContent,
                            page,
                            size,
                            (long) sfUsers.size(),
                            totalPages,
                            page >= totalPages - 1);
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for users, falling back to MySQL: {}",
                        e.getMessage());
            }
        }

        log.info("[DATA SOURCE: MYSQL] Fetching paged users (page={}, size={}) from MySQL database", page, size);
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> usersPage = userRepository.findAll(pageable);
        List<UserResponse> content = mapToUserResponseListWithRewards(usersPage.getContent());

        return new PagedResponse<>(
                content,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.isLast());
    }

    /**
     * Searches users across name and email matching the query string.
     *
     * @param query   Search query string
     * @param page    Page index
     * @param size    Page size
     * @param sortBy  Sort field
     * @param sortDir Sort direction
     * @return {@link PagedResponse} containing matching user records
     */
    @Override
    public PagedResponse<UserResponse> searchUsers(String query, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> usersPage = userRepository.searchUsers(query, pageable);
        List<UserResponse> content = mapToUserResponseListWithRewards(usersPage.getContent());

        return new PagedResponse<>(
                content,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.isLast());
    }

    /**
     * Retrieves a user by their ID with caching enabled.
     *
     * @param id The user ID
     * @return The {@link UserResponse} DTO
     */
    @Override
    @Cacheable(value = "users", key = "#p0")
    public UserResponse getUserById(Long id) {
        log.info("CACHE MISS - Fetching user {} from DATABASE", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
        return mapToUserResponseWithRewards(user);
    }

    /**
     * Updates an existing user and syncs changes to Salesforce.
     *
     * @param id      The user ID to update
     * @param request The updated user payload
     * @return The updated {@link UserResponse} DTO
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#p0")
    public UserResponse updateUser(Long id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name());
        }
        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email());
        }

        User updatedUser = userRepository.save(user);
        UserResponse response = mapToUserResponseWithRewards(updatedUser);

        if (salesforceSyncDelegate != null) {
            try {
                salesforceSyncDelegate.syncContact(UserMapper.INSTANCE.toContactSObject(updatedUser));
                updatedUser.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                userRepository.save(updatedUser);
            } catch (Exception e) {
                int retryCount = updatedUser.getSalesforceRetryCount() + 1;
                updatedUser.setSalesforceRetryCount(retryCount);
                updatedUser.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                userRepository.save(updatedUser);
                log.error("Salesforce dual-write failed for user update [User ID: {}, UUID: {}, Operation: UPDATE, RetryCount: {}]: {}",
                        updatedUser.getId(), updatedUser.getUuid(), retryCount, e.getMessage());
            }
        }

        return response;
    }

    /**
     * Deletes a user by their ID and evicts their cache entry.
     *
     * @param id The user ID to delete
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#p0")
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
        UUID userUuid = user.getUuid();
        userRepository.delete(user);

        if (salesforceSyncDelegate != null && userUuid != null) {
            try {
                salesforceSyncDelegate.deleteUser(userUuid);
            } catch (Exception e) {
                log.error("Failed to delete user from Salesforce [User ID: {}, UUID: {}]: {}", id, userUuid,
                        e.getMessage());
            }
        }
    }

    /**
     * Retrieves a user by their unique email address.
     *
     * @param email The user's email address
     * @return The matching {@link UserResponse} DTO
     */
    @Override
    public UserResponse getUserByEmail(String email) {
        return getUserByIdentifier(email);
    }

    @Override
    public UserResponse getUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identifier must not be empty");
        }
        User user = userProviderRepository.findByProviderId(identifier)
                .flatMap(up -> userRepository.findByUuid(up.getUserUuid()))
                .or(() -> {
                    if (identifier.contains("|")) {
                        String rawId = identifier.substring(identifier.indexOf('|') + 1);
                        if (!rawId.isBlank()) {
                            return userProviderRepository.findByProviderId(rawId)
                                    .flatMap(up -> userRepository.findByUuid(up.getUserUuid()));
                        }
                    }
                    return Optional.empty();
                })
                .or(() -> userRepository.findByEmail(identifier))
                .or(() -> {
                    try {
                        UUID uuid = UUID.fromString(identifier);
                        return userRepository.findByUuid(uuid);
                    } catch (IllegalArgumentException ignored) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with identifier: " + identifier));
        return mapToUserResponseWithRewards(user);
    }

    /**
     * Updates an existing user's role administratively in MySQL and syncs changes
     * to Salesforce.
     * Note: Auth0 is the source of truth for authentication/authorization; MySQL to
     * Auth0 role sync is removed/disabled.
     *
     * @param id      The user ID to update
     * @param newRole The new role
     * @return The updated {@link UserResponse} DTO
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#p0")
    public UserResponse updateUserRole(Long id, RoleEnum newRole) {
        if (newRole == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must not be null");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        user.setRole(newRole);
        User updatedUser = userRepository.save(user);
        UserResponse response = mapToUserResponseWithRewards(updatedUser);

        if (salesforceSyncDelegate != null) {
            try {
                salesforceSyncDelegate.syncContact(UserMapper.INSTANCE.toContactSObject(updatedUser));
                updatedUser.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                userRepository.save(updatedUser);
            } catch (Exception e) {
                int retryCount = updatedUser.getSalesforceRetryCount() + 1;
                updatedUser.setSalesforceRetryCount(retryCount);
                updatedUser.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                userRepository.save(updatedUser);
                log.error(
                        "Salesforce dual-write failed for user role update [User ID: {}, UUID: {}, Operation: UPDATE, RetryCount: {}]: {}",
                        updatedUser.getId(), updatedUser.getUuid(), retryCount, e.getMessage());
            }
        }

        return response;
    }

    /**
     * Synchronizes a user after Auth0 login to both MySQL and Salesforce.
     * External identity mapping is handled exclusively via user_provider
     * (provider=AUTH0, provider_id=sub).
     * Application user details (uuid, email, name, role) are stored in user table.
     * Existing user id, uuid, name, email, and role are NEVER overwritten on login.
     *
     * @param sub       The Auth0 subject identifier
     * @param email     The user's email
     * @param name      The user's full name
     * @param tokenRole The role extracted from Auth0 JWT for new user creation
     * @return The persisted {@link User} entity
     */
    @Override
    @Transactional
    public User syncAuth0User(String sub, String email, String name, RoleEnum tokenRole) {
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("Auth0 sub claim must not be empty");
        }

        // 1. Primary lookup by providerId in user_provider table (exact sub)
        var userProviderOpt = userProviderRepository.findByProviderId(sub);
        if (userProviderOpt.isPresent()) {
            UserProvider userProvider = userProviderOpt.get();
            return userRepository.findByUuid(userProvider.getUserUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "User not found for providerId: " + sub));
        }

        // 2. Lookup by raw provider ID if sub has a provider prefix (e.g. sub =
        // "google-oauth2|115993863360458947065", raw = "115993863360458947065")
        if (sub.contains("|")) {
            String rawId = sub.substring(sub.indexOf('|') + 1);
            if (!rawId.isBlank()) {
                var rawProviderOpt = userProviderRepository.findByProviderId(rawId);
                if (rawProviderOpt.isEmpty() && sub.lastIndexOf('|') != sub.indexOf('|')) {
                    String lastRawId = sub.substring(sub.lastIndexOf('|') + 1);
                    if (!lastRawId.isBlank()) {
                        rawProviderOpt = userProviderRepository.findByProviderId(lastRawId);
                    }
                }
                if (rawProviderOpt.isPresent()) {
                    UserProvider rawProvider = rawProviderOpt.get();
                    User existing = userRepository.findByUuid(rawProvider.getUserUuid())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "User not found for raw providerId: " + rawProvider.getProviderId()));

                    // Insert the new provider mapping for this existing user if not already present
                    var existingSubMapping = userProviderRepository.findByProviderId(sub);
                    if (existingSubMapping.isEmpty()) {
                        UserProvider newProvider = UserProvider.builder()
                                .userUuid(existing.getUuid())
                                .provider(AuthProvider.AUTH0)
                                .providerId(sub)
                                .build();
                        userProviderRepository.save(newProvider);
                    }

                    return existing;
                }
            }
        }

        // 3. Fallback lookup by email for linking existing accounts
        if (email != null && !email.isBlank()) {
            var userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                User existing = userByEmail.get();

                // Create a new user_provider mapping for this providerId if not already present.
                // Multiple provider IDs for the same user are preserved as separate records.
                // Do NOT modify user fields (name, email, role, etc.).
                // Do NOT update or sync Salesforce.
                var existingMapping = userProviderRepository.findByProviderId(sub);
                if (existingMapping.isEmpty()) {
                    UserProvider newProvider = UserProvider.builder()
                            .userUuid(existing.getUuid())
                            .provider(AuthProvider.AUTH0)
                            .providerId(sub)
                            .build();
                    userProviderRepository.save(newProvider);
                }

                return existing;
            }
        }

        // 4. No existing user -> create new User with role from Auth0 (or default USER)
        // and UserProvider link
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required to create a new user");
        }

        RoleEnum initialRole = (tokenRole != null) ? tokenRole : RoleEnum.USER;
        String effectiveName = (name != null && !name.isBlank()) ? name : "User";

        User newUser = User.builder()
                .email(email)
                .name(effectiveName)
                .role(initialRole)
                .build();

        User savedUser = userRepository.save(newUser);

        UserProvider newProvider = UserProvider.builder()
                .userUuid(savedUser.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId(sub)
                .build();
        userProviderRepository.save(newProvider);

        syncUserToSalesforce(savedUser);
        return savedUser;
    }

    @Override
    @Transactional
    public UserResponse getCurrentUser(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                email = jwt.getClaimAsString("https://library.kovanlabs.com/email");
            }
            if (email == null || email.isBlank()) {
                email = jwt.getClaimAsString("https://library-management.com/email");
            }

            String customUsername = getHeaderFromRequestContext("X-User-Username");
            if (customUsername == null || customUsername.isBlank()) {
                customUsername = getHeaderFromRequestContext("X-User-Name");
            }

            String name = (customUsername != null && !customUsername.isBlank()) ? customUsername.trim() : null;
            if (name == null || name.isBlank()) {
                name = jwt.getClaimAsString("name");
            }
            if (name == null || name.isBlank()) {
                name = jwt.getClaimAsString("nickname");
            }
            if (name == null || name.isBlank()) {
                name = jwt.getClaimAsString("https://library.kovanlabs.com/name");
            }

            RoleEnum role = extractRoleFromJwt(jwt);

            User syncedUser = syncAuth0User(sub, email, name, role);
            return mapToUserResponseWithRewards(syncedUser);
        }

        return getUserByIdentifier(principal.getName());
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUser(Principal principal, UserRequest request) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        UserResponse currentUser = getCurrentUser(principal);
        if (currentUser == null || currentUser.id() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");
        }
        return updateUser(currentUser.id(), request);
    }

    private RoleEnum extractRoleFromJwt(Jwt jwt) {
        List<String> candidates = new ArrayList<>();
        candidates.addAll(extractRolesList(jwt.getClaims().get("https://library.kovanlabs.com/roles")));
        candidates.addAll(extractRolesList(jwt.getClaims().get("https://library.kovanlabs.com/role")));
        candidates.addAll(extractRolesList(jwt.getClaims().get("roles")));
        candidates.addAll(extractRolesList(jwt.getClaims().get("role")));
        candidates.addAll(extractRolesList(jwt.getClaims().get("permissions")));

        if (jwt.getClaims().get("app_metadata") instanceof Map<?, ?> appMeta) {
            candidates.addAll(extractRolesList(appMeta.get("role")));
            candidates.addAll(extractRolesList(appMeta.get("roles")));
        }

        // Check ADMIN
        for (String r : candidates) {
            String clean = r.toUpperCase().replace("ROLE_", "").trim();
            if (clean.equals("ADMIN") || clean.contains("ADMIN") || clean.equals("BOOKS:WRITE")) {
                return RoleEnum.ADMIN;
            }
        }
        // Then check USER
        for (String r : candidates) {
            String clean = r.toUpperCase().replace("ROLE_", "").trim();
            if (clean.equals("USER") || clean.contains("USER")) {
                return RoleEnum.USER;
            }
        }
        return null;
    }

    private List<String> extractRolesList(Object claim) {
        if (claim instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String)
                    .map(item -> (String) item)
                    .toList();
        } else if (claim instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return Collections.emptyList();
    }

    private void syncUserToSalesforce(User user) {
        if (salesforceSyncDelegate != null && user != null && user.getUuid() != null) {
            try {
                salesforceSyncDelegate.syncContact(UserMapper.INSTANCE.toContactSObject(user));
                user.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                userRepository.save(user);
            } catch (Exception e) {
                int retryCount = user.getSalesforceRetryCount() + 1;
                user.setSalesforceRetryCount(retryCount);
                user.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                userRepository.save(user);
                log.error(
                        "Salesforce dual-write failed for user sync [User ID: {}, UUID: {}, Operation: SYNC, RetryCount: {}]: {}",
                        user.getId(), user.getUuid(), retryCount, e.getMessage());
            }
        }
    }

    private String getHeaderFromRequestContext(String headerName) {
        try {
            var requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                if (request != null) {
                    return request.getHeader(headerName);
                }
            }
        } catch (Exception ignored) {
            // Ignored outside web request context
        }
        return null;
    }
}
