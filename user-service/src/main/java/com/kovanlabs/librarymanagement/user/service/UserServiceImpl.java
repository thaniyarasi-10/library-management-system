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
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final RewardRepository rewardRepository;
    private final PasswordEncoder passwordEncoder;
    private final SalesforceUserSyncDelegate salesforceSyncDelegate;
    private final Auth0RoleSyncDelegate auth0RoleSyncDelegate;

    /**
     * Constructs a new {@link UserServiceImpl} with injected dependencies.
     *
     * @param userRepository Repository for User database operations
     * @param userProviderRepository Repository for UserProvider database operations
     * @param rewardRepository Repository for calculating user reward points
     * @param passwordEncoder Password hashing encoder
     * @param salesforceSyncDelegate Optional delegate for synchronizing changes to Salesforce
     * @param auth0RoleSyncDelegate Optional delegate for synchronizing user roles to Auth0
     */
    @Autowired
    public UserServiceImpl(
            UserRepository userRepository,
            UserProviderRepository userProviderRepository,
            RewardRepository rewardRepository,
            @Lazy PasswordEncoder passwordEncoder,
            @Autowired(required = false) SalesforceUserSyncDelegate salesforceSyncDelegate,
            @Autowired(required = false) Auth0RoleSyncDelegate auth0RoleSyncDelegate) {

        this.userRepository = userRepository;
        this.userProviderRepository = userProviderRepository;
        this.rewardRepository = rewardRepository;
        this.passwordEncoder = passwordEncoder;
        this.salesforceSyncDelegate = salesforceSyncDelegate;
        this.auth0RoleSyncDelegate = auth0RoleSyncDelegate;
    }

    /**
     * Helper method to map a single User entity to {@link UserResponse} along with their calculated reward points.
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
                points);
    }

    /**
     * Helper method to batch map a list of User entities to {@link UserResponse} DTOs with reward points.
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
                user.getUuid() != null ? rewardMap.getOrDefault(user.getUuid(), 0) : 0)).collect(Collectors.toList());
    }

    /**
     * Creates a new user, encodes their password, saves to MySQL, and triggers dual-write sync with Salesforce if configured.
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
            if (request.password() != null) {
                user.setPassword(passwordEncoder.encode(request.password()));
            }
        }
        User savedUser = userRepository.save(user);
        UserResponse response = mapToUserResponseWithRewards(savedUser);

        if (salesforceSyncDelegate != null) {
            try {
                salesforceSyncDelegate.syncUser(response);
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
     * Fetches all users from Salesforce if delegate is active and records exist, otherwise falls back to MySQL.
     *
     * @return List of {@link UserResponse} DTOs
     */
    @Override
    public List<UserResponse> getAllUsers() {
        if (salesforceSyncDelegate != null) {
            try {
                List<UserResponse> sfUsers = salesforceSyncDelegate.fetchUsersFromSalesforce();
                if (sfUsers != null && !sfUsers.isEmpty()) {
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} users from Salesforce SOQL",
                            sfUsers.size());
                    return sfUsers;
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
     * Fetches a paginated list of users from Salesforce (with in-memory paging) or MySQL database.
     *
     * @param page Zero-based page number
     * @param size Number of items per page
     * @param sortBy Field to sort by
     * @param sortDir Sort direction ("asc" or "desc")
     * @return {@link PagedResponse} of {@link UserResponse}
     */
    @Override
    public PagedResponse<UserResponse> getAllUsers(int page, int size, String sortBy, String sortDir) {
        if (salesforceSyncDelegate != null) {
            try {
                List<UserResponse> sfUsers = salesforceSyncDelegate.fetchUsersFromSalesforce();
                if (sfUsers != null && !sfUsers.isEmpty()) {
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
     * @param query Search query string
     * @param page Page index
     * @param size Page size
     * @param sortBy Sort field
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
     * @param id The user ID to update
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
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        User updatedUser = userRepository.save(user);
        UserResponse response = mapToUserResponseWithRewards(updatedUser);

        if (salesforceSyncDelegate != null) {
            try {
                salesforceSyncDelegate.syncUser(response);
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
        userRepository.delete(user);
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
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with identifier: " + identifier));
        return mapToUserResponseWithRewards(user);
    }

    /**
     * Updates an existing user's role administratively in MySQL and synchronizes the change to Auth0 app_metadata.
     *
     * @param id The user ID to update
     * @param newRole The new authoritative role
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

        // Sync new role to all Auth0 identities linked to this user
        if (auth0RoleSyncDelegate != null && updatedUser.getUuid() != null) {
            List<UserProvider> providers = userProviderRepository.findByUserUuid(updatedUser.getUuid());
            for (UserProvider provider : providers) {
                if (provider.getProvider() == AuthProvider.AUTH0 && provider.getProviderId() != null) {
                    try {
                        auth0RoleSyncDelegate.syncUserRole(provider.getProviderId(), newRole);
                    } catch (Exception e) {
                        log.error("Failed to sync role update to Auth0 for user {}: {}", provider.getProviderId(), e.getMessage());
                    }
                }
            }
        }

        return response;
    }

    /**
     * Looks up an existing user by Auth0 provider ID (sub) or email, creates/updates user and links provider.
     * MySQL user.role is the source of truth; incoming token claims never overwrite user.role.
     * Synchronizes authoritative MySQL role to Auth0 app_metadata.role on user creation or identity linking.
     *
     * @param sub The Auth0 subject identifier
     * @param email The user's email
     * @param name The user's full name
     * @return The persisted {@link User} entity
     */
    @Override
    @Transactional
    public User syncAuth0User(String sub, String email, String name) {
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("Auth0 sub claim must not be empty");
        }

        // 1. Primary lookup by providerId in user_provider table
        var userProviderOpt = userProviderRepository.findByProviderId(sub);
        if (userProviderOpt.isPresent()) {
            UserProvider userProvider = userProviderOpt.get();
            User existing = userRepository.findByUuid(userProvider.getUserUuid())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for providerId: " + sub));
            boolean changed = false;
            if (email != null && !email.isBlank() && !email.equals(existing.getEmail())) {
                existing.setEmail(email);
                changed = true;
            }
            if (name != null && !name.isBlank() && (existing.getName() == null || !name.equals(existing.getName()))) {
                existing.setName(name);
                changed = true;
            }
            // Do NOT modify existing.getRole() based on incoming claims
            return changed ? userRepository.save(existing) : existing;
        }

        // 2. Fallback lookup by email for linking existing accounts
        if (email != null && !email.isBlank()) {
            var userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                User existing = userByEmail.get();
                if (name != null && !name.isBlank() && (existing.getName() == null || existing.getName().isBlank())) {
                    existing.setName(name);
                }
                User saved = userRepository.save(existing);

                // Create user_provider link if not already present
                if (userProviderRepository.findByUserUuidAndProvider(saved.getUuid(), AuthProvider.AUTH0).isEmpty()) {
                    UserProvider newProvider = UserProvider.builder()
                            .userUuid(saved.getUuid())
                            .provider(AuthProvider.AUTH0)
                            .providerId(sub)
                            .build();
                    userProviderRepository.save(newProvider);
                }

                // Sync existing MySQL role to Auth0 app_metadata
                if (auth0RoleSyncDelegate != null) {
                    try {
                        auth0RoleSyncDelegate.syncUserRole(sub, saved.getRole());
                    } catch (Exception e) {
                        log.error("Failed to sync role to Auth0 during account linking for user {}: {}", sub, e.getMessage());
                    }
                }

                return saved;
            }
        }

        // 3. No existing user -> create new User with USER role and UserProvider link
        String effectiveEmail = (email != null && !email.isBlank()) ? email : sub + "@auth0.user";
        String effectiveName = (name != null && !name.isBlank()) ? name : "User";

        User newUser = User.builder()
                .email(effectiveEmail)
                .name(effectiveName)
                .role(RoleEnum.USER)
                .build();

        User savedUser = userRepository.save(newUser);

        UserProvider newProvider = UserProvider.builder()
                .userUuid(savedUser.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId(sub)
                .build();
        userProviderRepository.save(newProvider);

        // Sync default USER role to Auth0 app_metadata
        if (auth0RoleSyncDelegate != null) {
            try {
                auth0RoleSyncDelegate.syncUserRole(sub, RoleEnum.USER);
            } catch (Exception e) {
                log.error("Failed to sync initial role to Auth0 for new user {}: {}", sub, e.getMessage());
            }
        }

        return savedUser;
    }
}
