package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
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

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Primary implementation of {@link UserService} managing user lifecycle,
 * authentication synchronization, role mappings, account deactivations,
 * reactivation approval workflows, and external Salesforce CRM synchronization.
 */
@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    public static final int REACTIVATION_RESTRICTION_DAYS = 10;
    public static final String AUTH0_CLAIM_NAMESPACE = "https://library-management.com";
    public static final String CLAIM_ROLES = AUTH0_CLAIM_NAMESPACE + "/roles";
    public static final String CLAIM_ROLE = AUTH0_CLAIM_NAMESPACE + "/role";
    public static final String CLAIM_EMAIL = AUTH0_CLAIM_NAMESPACE + "/email";
    public static final String CLAIM_NAME = AUTH0_CLAIM_NAMESPACE + "/name";

    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final RewardRepository rewardRepository;
    private final FineRepository fineRepository;
    private final SalesforceSync salesforceSyncDelegate;
    private final AccountDeactivationService accountDeactivationService;

    /**
     * Maps a single User entity to {@link UserResponse} with reward points and
     * pending fine status via {@link UserMapper}.
     */
    private UserResponse toUserResponse(User user) {
        if (Objects.isNull(user)) {
            return null;
        }
        Integer points = Objects.nonNull(user.getUuid()) && Objects.nonNull(rewardRepository)
                ? rewardRepository.findByUserUuid(user.getUuid()).map(Reward::getPoints).orElse(0)
                : 0;
        BigDecimal pendingFineAmount = BigDecimal.ZERO;
        if (Objects.nonNull(user.getUuid()) && Objects.nonNull(fineRepository)) {
            List<Fine> pendingFines = fineRepository.findByUserUuidAndStatus(user.getUuid(), FineStatus.PENDING);
            if (Objects.nonNull(pendingFines)) {
                pendingFineAmount = pendingFines.stream()
                        .map(Fine::getPendingFineAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }
        boolean hasPendingFines = pendingFineAmount.compareTo(BigDecimal.ZERO) > 0;
        return UserMapper.INSTANCE.mapToResponse(user, points, hasPendingFines, pendingFineAmount.doubleValue());
    }

    /**
     * Maps a list of User entities to {@link UserResponse} DTOs with reward points
     * via {@link UserMapper}.
     */
    private List<UserResponse> toUserResponseList(List<User> users) {
        if (Objects.isNull(users) || users.isEmpty()) {
            return List.of();
        }
        List<String> uuids = users.stream()
                .map(User::getUuid)
                .filter(Objects::nonNull)
                .toList();

        Map<String, Integer> rewardMap = Objects.nonNull(rewardRepository)
                ? rewardRepository.findByUserUuidIn(uuids).stream()
                        .collect(Collectors.toMap(
                                Reward::getUserUuid,
                                Reward::getPoints,
                                (p1, p2) -> p1))
                : Collections.emptyMap();

        return UserMapper.INSTANCE.mapToResponse(users, rewardMap);
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
        if (Objects.nonNull(user)) {
            user.setRole(RoleEnum.USER);
        }
        User savedUser = userRepository.save(user);
        UserResponse response = toUserResponse(savedUser);

        if (Objects.nonNull(savedUser) && Objects.nonNull(salesforceSyncDelegate)) {
            try {
                salesforceSyncDelegate.syncContact(UserMapper.INSTANCE.toContactSObject(savedUser));
                savedUser.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                userRepository.save(savedUser);
            } catch (Exception e) {
                int retryCount = savedUser.getSalesforceRetryCount() + 1;
                savedUser.setSalesforceRetryCount(retryCount);
                savedUser.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                userRepository.save(savedUser);
                log.error(
                        "Salesforce dual-write failed for user creation [User ID: {}, UUID: {}, Operation: CREATE, RetryCount: {}]: {}",
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
        if (Objects.nonNull(salesforceSyncDelegate)) {
            try {
                var sfContacts = salesforceSyncDelegate.fetchContactsFromSalesforce();
                if (Objects.nonNull(sfContacts) && !sfContacts.isEmpty()) {
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
        return toUserResponseList(userRepository.findAll());
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
        if (Objects.nonNull(salesforceSyncDelegate)) {
            try {
                var sfContacts = salesforceSyncDelegate.fetchContactsFromSalesforce();
                if (Objects.nonNull(sfContacts) && !sfContacts.isEmpty()) {
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
        List<UserResponse> content = toUserResponseList(usersPage.getContent());

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
        List<UserResponse> content = toUserResponseList(usersPage.getContent());

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
        return toUserResponse(user);
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

        if (Objects.nonNull(request.name()) && !request.name().isBlank()) {
            user.setName(request.name());
        }
        if (Objects.nonNull(request.email()) && !request.email().isBlank()) {
            user.setEmail(request.email());
        }
        if (Objects.nonNull(request.phone())) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }

        User updatedUser = userRepository.save(user);
        UserResponse response = toUserResponse(updatedUser);

        if (Objects.nonNull(salesforceSyncDelegate)) {
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
                        "Salesforce dual-write failed for user update [User ID: {}, UUID: {}, Operation: UPDATE, RetryCount: {}]: {}",
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
        String userUuid = user.getUuid();
        userRepository.delete(user);

        if (Objects.nonNull(salesforceSyncDelegate) && Objects.nonNull(userUuid)) {
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
        return toUserResponse(findUserEntityByIdentifier(identifier));
    }

    private User findUserEntityByIdentifier(String identifier) {
        if (Objects.isNull(identifier) || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identifier must not be empty");
        }
        return userProviderRepository.findByProviderId(identifier)
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
                .or(() -> userRepository.findByUuid(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with identifier: " + identifier));
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
        if (Objects.isNull(newRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must not be null");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        user.setRole(newRole);
        User updatedUser = userRepository.save(user);
        UserResponse response = toUserResponse(updatedUser);

        if (Objects.nonNull(salesforceSyncDelegate)) {
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
        if (Objects.isNull(sub) || sub.isBlank()) {
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
        if (Objects.nonNull(sub) && sub.contains("|")) {
            int firstPipeIndex = sub.indexOf('|');
            int lastPipeIndex = sub.lastIndexOf('|');
            String rawId = (firstPipeIndex >= 0 && firstPipeIndex < sub.length() - 1)
                    ? sub.substring(firstPipeIndex + 1).trim()
                    : "";

            if (!rawId.isBlank()) {
                var rawProviderOpt = userProviderRepository.findByProviderId(rawId);
                if (rawProviderOpt.isEmpty() && lastPipeIndex > firstPipeIndex && lastPipeIndex < sub.length() - 1) {
                    String lastRawId = sub.substring(lastPipeIndex + 1).trim();
                    if (!lastRawId.isBlank() && !lastRawId.equals(rawId)) {
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
        if (Objects.nonNull(email) && !email.isBlank()) {
            var userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                User existing = userByEmail.get();

                // Create a new user_provider mapping for this providerId if not already
                // present.
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
        if (Objects.isNull(email) || email.isBlank()) {
            throw new IllegalArgumentException("Email is required to create a new user");
        }

        RoleEnum initialRole = Objects.nonNull(tokenRole) ? tokenRole : RoleEnum.USER;
        String effectiveName = (Objects.nonNull(name) && !name.isBlank()) ? name : "User";

        User newUser = User.builder()
                .email(email)
                .name(effectiveName)
                .role(initialRole)
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(java.time.LocalDateTime.now())
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

    /**
     * Retrieves the current user's profile without altering login activity metadata
     * or timestamps.
     * Safe to call repeatedly on /me or /api/auth/me without resetting the account
     * inactivity period.
     *
     * @param principal The security principal representing the current user
     * @return The {@link UserResponse} DTO
     * @throws ResponseStatusException 401 UNAUTHORIZED if principal is null
     */
    @Override
    @Transactional
    public UserResponse getCurrentUser(Principal principal) {
        if (Objects.isNull(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (Objects.isNull(email) || email.isBlank()) {
                email = jwt.getClaimAsString(CLAIM_EMAIL);
            }
            if (Objects.isNull(email) || email.isBlank()) {
                email = getHeaderFromRequestContext("X-User-Email");
            }

            String customUsername = getHeaderFromRequestContext("X-User-Username");
            if (Objects.isNull(customUsername) || customUsername.isBlank()) {
                customUsername = getHeaderFromRequestContext("X-User-Name");
            }

            String name = (Objects.nonNull(customUsername) && !customUsername.isBlank()) ? customUsername.trim() : null;
            if (Objects.isNull(name) || name.isBlank()) {
                name = jwt.getClaimAsString("name");
            }
            if (Objects.isNull(name) || name.isBlank()) {
                name = jwt.getClaimAsString("nickname");
            }
            if (Objects.isNull(name) || name.isBlank()) {
                name = jwt.getClaimAsString(CLAIM_NAME);
            }

            RoleEnum role = extractRoleFromJwt(jwt);

            User syncedUser = syncAuth0User(sub, email, name, role);
            syncedUser = reconcileSalesforcePendingReactivation(syncedUser);
            syncedUser = checkAndTransitionExpiredDeactivation(syncedUser);
            return toUserResponse(syncedUser);
        }

        User user = findUserEntityByIdentifier(principal.getName());
        user = reconcileSalesforcePendingReactivation(user);
        user = checkAndTransitionExpiredDeactivation(user);
        return toUserResponse(user);
    }

    /**
     * Records a genuine successful login completion event for an authenticated
     * user.
     * Updates {@code last_login_at} timestamp and clears any pending inactivity
     * warning state.
     * Separated from {@link #getCurrentUser(Principal)} to prevent repeated /me
     * profile reads
     * from inadvertently resetting the inactivity countdown.
     *
     * @param principal The authenticated security principal
     * @return The updated {@link UserResponse} DTO
     * @throws ResponseStatusException 401 UNAUTHORIZED if principal is null
     */
    @Override
    @Transactional
    public UserResponse recordLogin(Principal principal) {
        if (Objects.isNull(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        User user;
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            if (Objects.isNull(email) || email.isBlank()) {
                email = jwt.getClaimAsString(CLAIM_EMAIL);
            }
            if (Objects.isNull(email) || email.isBlank()) {
                email = getHeaderFromRequestContext("X-User-Email");
            }

            String customUsername = getHeaderFromRequestContext("X-User-Username");
            if (Objects.isNull(customUsername) || customUsername.isBlank()) {
                customUsername = getHeaderFromRequestContext("X-User-Name");
            }

            String name = (Objects.nonNull(customUsername) && !customUsername.isBlank()) ? customUsername.trim() : null;
            if (Objects.isNull(name) || name.isBlank()) {
                name = jwt.getClaimAsString("name");
            }
            if (Objects.isNull(name) || name.isBlank()) {
                name = jwt.getClaimAsString("nickname");
            }
            if (Objects.isNull(name) || name.isBlank()) {
                name = jwt.getClaimAsString(CLAIM_NAME);
            }

            RoleEnum role = extractRoleFromJwt(jwt);

            user = syncAuth0User(sub, email, name, role);
        } else {
            user = findUserEntityByIdentifier(principal.getName());
        }

        user = reconcileSalesforcePendingReactivation(user);

        if (Objects.nonNull(user) && user.getStatus() == AccountStatus.ACTIVE) {
            user.setLastLoginAt(LocalDateTime.now());
            user.setInactivityWarningSentAt(null);
            user = userRepository.save(user);
            syncUserToSalesforce(user);
        } else if (Objects.nonNull(user) && user.getStatus() == AccountStatus.DEACTIVATED) {
            user = checkAndTransitionExpiredDeactivation(user);
        }

        return toUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateCurrentUser(Principal principal, UserRequest request) {
        if (Objects.isNull(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        UserResponse currentUser = getCurrentUser(principal);
        if (Objects.isNull(currentUser) || Objects.isNull(currentUser.id())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(currentUser.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Account is " + currentUser.status() + ". Profile updates are restricted to ACTIVE accounts.");
        }
        return updateUser(currentUser.id(), request);
    }

    private RoleEnum extractRoleFromJwt(Jwt jwt) {
        List<String> candidates = new ArrayList<>();
        candidates.addAll(extractRolesList(jwt.getClaims().get(CLAIM_ROLES)));
        candidates.addAll(extractRolesList(jwt.getClaims().get(CLAIM_ROLE)));
        candidates.addAll(extractRolesList(jwt.getClaims().get("roles")));
        candidates.addAll(extractRolesList(jwt.getClaims().get("role")));
        candidates.addAll(extractRolesList(jwt.getClaims().get("permissions")));

        if (jwt.getClaims().get("app_metadata") instanceof Map<?, ?> appMeta) {
            candidates.addAll(extractRolesList(appMeta.get("role")));
            candidates.addAll(extractRolesList(appMeta.get("roles")));
        }

        boolean isAdmin = candidates.stream()
                .map(r -> r.toUpperCase().replace("ROLE_", "").trim())
                .anyMatch(clean -> clean.equals("ADMIN") || clean.contains("ADMIN") || clean.equals("BOOKS:WRITE"));
        if (isAdmin) {
            return RoleEnum.ADMIN;
        }

        boolean isUser = candidates.stream()
                .map(r -> r.toUpperCase().replace("ROLE_", "").trim())
                .anyMatch(clean -> clean.equals("USER") || clean.contains("USER"));
        return isUser ? RoleEnum.USER : null;
    }

    private List<String> extractRolesList(Object claim) {
        return claim instanceof List<?> list
                ? list.stream().filter(item -> item instanceof String).map(item -> (String) item).toList()
                : (claim instanceof String s && !s.isBlank() ? List.of(s) : Collections.emptyList());
    }

    private void resetUserRewardPoints(String userUuid) {
        if (Objects.nonNull(userUuid) && Objects.nonNull(rewardRepository)) {
            int updated = rewardRepository.resetPoints(userUuid);
            if (updated == 0) {
                rewardRepository.save(Reward.builder()
                        .userUuid(userUuid)
                        .points(0)
                        .build());
            }
            log.info("Reset reward points to 0 for user [UUID: {}]", userUuid);
        }
    }

    private void syncUserToSalesforce(User user) {
        if (Objects.nonNull(salesforceSyncDelegate) && Objects.nonNull(user) && Objects.nonNull(user.getUuid())) {
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

    /**
     * Approves and executes user account reactivation by restoring ACTIVE status and setting request status to APPROVED.
     * Clears pending inactivity warning timestamps, resets last login baseline to the current time,
     * updates MySQL transactionally, and synchronizes status and request status to Salesforce.
     *
     * @param id The primary key identifier of the user to reactivate
     * @return The updated {@link UserResponse}
     * @throws ResponseStatusException 404 NOT_FOUND if the user does not exist
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#p0")
    public UserResponse reactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        user = reconcileSalesforcePendingReactivation(user);

        if (user.getStatus() == AccountStatus.PERMANENTLY_DEACTIVATED
                && user.getReactivationRequestStatus() != ReactivationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is permanently deactivated and cannot be reactivated");
        }
        user.setStatus(AccountStatus.ACTIVE);
        user.setDeactivatedAt(null);
        user.setReactivationRequestStatus(ReactivationRequestStatus.APPROVED);
        user.setInactivityWarningSentAt(null);
        user.setLastLoginAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);
        syncUserToSalesforce(savedUser);
        log.info("Successfully reactivated user account ID: {}, Email: {}", id, savedUser.getEmail());
        return toUserResponse(savedUser);
    }

    /**
     * Deactivates a user account, setting its status to DEACTIVATED, recording deactivatedAt timestamp,
     * and clearing request status. Updates MySQL transactionally and synchronizes DEACTIVATED status to Salesforce.
     *
     * @param id The primary key identifier of the user to deactivate
     * @return The updated {@link UserResponse}
     * @throws ResponseStatusException 404 NOT_FOUND if the user does not exist
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#p0")
    public UserResponse deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
        user.setStatus(AccountStatus.DEACTIVATED);
        user.setDeactivatedAt(LocalDateTime.now());
        user.setReactivationRequestStatus(null);
        resetUserRewardPoints(user.getUuid());
        User savedUser = userRepository.save(user);
        syncUserToSalesforce(savedUser);
        log.info("Successfully deactivated user account ID: {}, Email: {}", id, savedUser.getEmail());
        return toUserResponse(savedUser);
    }

    /**
     * Submits an account reactivation request on behalf of the currently authenticated user.
     * <p>
     * Verification rules:
     * <ul>
     *     <li>Account must currently be in DEACTIVATED status (cannot be PERMANENTLY_DEACTIVATED or ACTIVE).</li>
     *     <li>Reactivation request must be submitted within 10 days of account deactivation date. If the 10-day
     *         window has expired, the status is updated to PERMANENTLY_DEACTIVATED and synchronized to Salesforce.</li>
     *     <li>User must not have any outstanding unpaid fines. If pending fines exist, requests are rejected with 400 BAD_REQUEST.</li>
     *     <li>User must not have a request already pending review to prevent duplicate pending requests.</li>
     * </ul>
     * When submitted, sets {@code reactivation_request_status} to {@code PENDING} in MySQL and
     * synchronizes {@code Reactivation_Request_Status__c = PENDING} to Salesforce.
     *
     * @param principal The security principal representing the requesting user
     * @return The updated {@link UserResponse}
     * @throws ResponseStatusException 401 UNAUTHORIZED if unauthenticated, 404 NOT_FOUND if user not found,
     *                                 or 400 BAD_REQUEST if account is permanently deactivated, expired, not deactivated, fines pending, or duplicate request
     */
    @Override
    @Transactional
    public UserResponse requestReactivation(Principal principal) {
        if (Objects.isNull(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        User user = null;
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Jwt jwt) {
            String sub = jwt.getSubject();
            var userProviderOpt = userProviderRepository.findByProviderId(sub);
            if (userProviderOpt.isPresent()) {
                user = userRepository.findByUuid(userProviderOpt.get().getUserUuid()).orElse(null);
            }
            if (Objects.isNull(user)) {
                String email = jwt.getClaimAsString("email");

                if (Objects.isNull(email) || email.isBlank()) {
                    email = jwt.getClaimAsString(CLAIM_EMAIL);
                }
                if (Objects.isNull(email) || email.isBlank()) {
                    email = getHeaderFromRequestContext("X-User-Email");
                }
                if (Objects.nonNull(email) && !email.isBlank()) {
                    user = userRepository.findByEmail(email).orElse(null);
                }
            }
        } else {
            user = userRepository.findByEmail(principal.getName())
                    .or(() -> userRepository.findByUuid(principal.getName()))
                    .orElse(null);
        }

        if (Objects.isNull(user)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        if (user.getStatus() == AccountStatus.PERMANENTLY_DEACTIVATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is permanently deactivated and cannot be reactivated");
        }

        if (user.getStatus() != AccountStatus.DEACTIVATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account is not deactivated");
        }

        if (accountDeactivationService.isReactivationWindowExpired(user)) {
            user.setStatus(AccountStatus.PERMANENTLY_DEACTIVATED);
            user.setReactivationRequestStatus(null);
            User savedUser = userRepository.save(user);
            syncUserToSalesforce(savedUser);
            accountDeactivationService.notifyPermanentDeactivation(savedUser.getUuid());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reactivation period of 10 days has expired. Account is permanently deactivated.");
        }

        if (user.getReactivationRequestStatus() == ReactivationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reactivation request is already pending review");
        }

        BigDecimal pendingFineAmount = BigDecimal.ZERO;
        if (Objects.nonNull(user.getUuid())) {
            List<Fine> pendingFines = fineRepository.findByUserUuidAndStatus(user.getUuid(), FineStatus.PENDING);
            pendingFineAmount = pendingFines.stream()
                    .map(Fine::getPendingFineAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        if (pendingFineAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot request reactivation with pending fines. Please clear all pending fines first.");
        }

        user.setReactivationRequestStatus(ReactivationRequestStatus.PENDING);
        User savedUser = userRepository.save(user);
        syncUserToSalesforce(savedUser);
        log.info("User {} successfully requested account reactivation", savedUser.getEmail());
        return toUserResponse(savedUser);
    }

    /**
     * Retrieves all account reactivation requests awaiting administrative review.
     * Fetches Salesforce as the primary read source when Salesforce integration is configured,
     * falling back to MySQL if the Salesforce query fails or is unconfigured.
     *
     * @return List of {@link UserResponse} representing users with reactivation requests
     */
    @Override
    public List<UserResponse> getReactivationRequests() {
        if (Objects.nonNull(salesforceSyncDelegate)) {
            try {
                var sfContacts = salesforceSyncDelegate.fetchContactsFromSalesforce();
                if (Objects.nonNull(sfContacts) && !sfContacts.isEmpty()) {
                    List<ContactSObject> filteredContacts = sfContacts.stream()
                            .filter(c -> Objects.nonNull(c.getReactivationRequestStatus()) && !c.getReactivationRequestStatus().isBlank())
                            .toList();
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} reactivation requests from Salesforce SOQL",
                            filteredContacts.size());

                    for (ContactSObject contact : filteredContacts) {
                        if ("PENDING".equalsIgnoreCase(contact.getReactivationRequestStatus())) {
                            findUserByContact(contact).ifPresent(u -> {
                                if (u.getStatus() != AccountStatus.DEACTIVATED
                                        || u.getReactivationRequestStatus() != ReactivationRequestStatus.PENDING) {
                                    u.setStatus(AccountStatus.DEACTIVATED);
                                    u.setReactivationRequestStatus(ReactivationRequestStatus.PENDING);
                                    userRepository.save(u);
                                    log.info("Reconciled MySQL user ID {} ({}) to DEACTIVATED with PENDING request",
                                            u.getId(), u.getEmail());
                                }
                            });
                        }
                    }

                    return UserMapper.INSTANCE.toUserResponseList(filteredContacts);
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for reactivation requests, falling back to MySQL: {}",
                        e.getMessage());
            }
        }

        log.info("[DATA SOURCE: MYSQL] Fetching all reactivation requests from MySQL database");
        List<User> users = userRepository.findByReactivationRequestStatusIsNotNull();
        return toUserResponseList(users);
    }

    /**
     * Rejects an account reactivation request for a deactivated user, setting request status to REJECTED.
     * If the 10-day reactivation window has elapsed, sets status to PERMANENTLY_DEACTIVATED;
     * if still within the 10-day window, sets status to DEACTIVATED.
     * Persists to MySQL and synchronizes to Salesforce.
     *
     * @param id The primary key identifier of the user whose request is being rejected
     * @return The updated {@link UserResponse}
     * @throws ResponseStatusException 404 NOT_FOUND if the user does not exist
     */
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#p0")
    public UserResponse rejectReactivation(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));

        user = reconcileSalesforcePendingReactivation(user);

        if (accountDeactivationService.isReactivationWindowExpired(user)) {
            user.setStatus(AccountStatus.PERMANENTLY_DEACTIVATED);
            user.setReactivationRequestStatus(ReactivationRequestStatus.REJECTED);
        } else {
            user.setStatus(AccountStatus.DEACTIVATED);
            user.setReactivationRequestStatus(ReactivationRequestStatus.REJECTED);
        }
        resetUserRewardPoints(user.getUuid());
        User savedUser = userRepository.save(user);
        syncUserToSalesforce(savedUser);
        if (savedUser.getStatus() == AccountStatus.PERMANENTLY_DEACTIVATED) {
            accountDeactivationService.notifyPermanentDeactivation(savedUser.getUuid());
        }
        log.info("Successfully rejected account reactivation request for user ID: {}, Email: {}, Status: {}",
                id, savedUser.getEmail(), savedUser.getStatus());
        return toUserResponse(savedUser);
    }

    /**
     * Identifies accounts that have been deactivated for more than 10 days without a pending reactivation request,
     * updates their status to PERMANENTLY_DEACTIVATED in MySQL, and synchronizes the updated status to Salesforce.
     * Delegates to {@link AccountDeactivationService#processExpiredDeactivations()}.
     *
     * @return The number of accounts permanently deactivated
     */
    @Override
    @Transactional
    public int processExpiredDeactivations() {
        return accountDeactivationService.processExpiredDeactivations();
    }

    /**
     * Checks if a deactivated user account has exceeded the 10-day reactivation restriction window.
     * If exceeded, updates the status to {@link AccountStatus#PERMANENTLY_DEACTIVATED}, clears any pending
     * request status, persists the update to MySQL, and synchronizes to Salesforce.
     * Delegates to {@link AccountDeactivationService#checkAndTransitionExpiredDeactivation(User)}.
     *
     * @param user The user entity to verify
     * @return The updated user entity
     */
    private User checkAndTransitionExpiredDeactivation(User user) {
        return accountDeactivationService.checkAndTransitionExpiredDeactivation(user);
    }

    private String getHeaderFromRequestContext(String headerName) {
        try {
            return (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttributes
                    && Objects.nonNull(servletAttributes.getRequest()))
                            ? servletAttributes.getRequest().getHeader(headerName)
                            : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Optional<User> findUserByContact(ContactSObject contact) {
        if (Objects.isNull(contact)) {
            return Optional.empty();
        }
        if (Objects.nonNull(contact.getLegacyUserId())) {
            var byId = userRepository.findById(contact.getLegacyUserId());
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (Objects.nonNull(contact.getExternalUserUuid()) && !contact.getExternalUserUuid().isBlank()) {
            var byUuid = userRepository.findByUuid(contact.getExternalUserUuid());
            if (byUuid.isPresent()) {
                return byUuid;
            }
        }
        if (Objects.nonNull(contact.getEmail()) && !contact.getEmail().isBlank()) {
            return userRepository.findByEmail(contact.getEmail());
        }
        return Optional.empty();
    }

    private User reconcileSalesforcePendingReactivation(User user) {
        if (Objects.isNull(user) || user.getStatus() == AccountStatus.ACTIVE) {
            return user;
        }

        if (user.getStatus() == AccountStatus.DEACTIVATED
                && user.getReactivationRequestStatus() == ReactivationRequestStatus.PENDING) {
            return user;
        }

        if (Objects.nonNull(salesforceSyncDelegate)) {
            try {
                var sfContacts = salesforceSyncDelegate.fetchContactsFromSalesforce();
                if (Objects.nonNull(sfContacts)) {
                    final Long targetId = user.getId();
                    final String targetUuid = user.getUuid();
                    final String targetEmail = user.getEmail();

                    var pendingContactOpt = sfContacts.stream()
                            .filter(c -> (Objects.equals(c.getLegacyUserId(), targetId)
                                    || (Objects.nonNull(targetUuid) && Objects.equals(c.getExternalUserUuid(), targetUuid))
                                    || (Objects.nonNull(targetEmail) && Objects.nonNull(c.getEmail()) && c.getEmail().equalsIgnoreCase(targetEmail)))
                                    && "PENDING".equalsIgnoreCase(c.getReactivationRequestStatus()))
                            .findFirst();

                    if (pendingContactOpt.isPresent()) {
                        log.info("Reconciling user ID {} ({}) from Salesforce PENDING reactivation request.",
                                targetId, targetEmail);
                        user.setStatus(AccountStatus.DEACTIVATED);
                        user.setReactivationRequestStatus(ReactivationRequestStatus.PENDING);
                        return userRepository.save(user);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check Salesforce for pending reactivation status: {}", e.getMessage());
            }
        }
        return user;
    }
}
