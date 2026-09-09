package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import com.kovanlabs.librarymanagement.user.mapping.UserMapper;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final SalesforceUserSyncDelegate salesforceSyncDelegate;

    @Autowired
    public UserServiceImpl(
            UserRepository userRepository,
            @Lazy PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            @Autowired(required = false) SalesforceUserSyncDelegate salesforceSyncDelegate) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.salesforceSyncDelegate = salesforceSyncDelegate;
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public UserResponse createUser(UserRequest request) {
        User user = userMapper.mapToEntity(request);
        if (user != null && request.password() != null) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        User savedUser = userRepository.save(user);

        if (salesforceSyncDelegate != null) {
            try {
                salesforceSyncDelegate.syncUser(savedUser);
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for user creation: {}", e.getMessage());
            }
        }

        return userMapper.mapToResponse(savedUser);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        if (salesforceSyncDelegate != null) {
            try {
                List<UserResponse> sfUsers = salesforceSyncDelegate.fetchUsersFromSalesforce();
                if (sfUsers != null && !sfUsers.isEmpty()) {
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} users from Salesforce SOQL", sfUsers.size());
                    return sfUsers;
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for users, falling back to MySQL: {}", e.getMessage());
            }
        }
        log.info("[DATA SOURCE: MYSQL] Fetching users from MySQL database");
        return userMapper.mapToResponse(userRepository.findAll());
    }

    @Override
    public PagedResponse<UserResponse> getAllUsers(int page, int size, String sortBy, String sortDir) {
        if (salesforceSyncDelegate != null) {
            try {
                List<UserResponse> sfUsers = salesforceSyncDelegate.fetchUsersFromSalesforce();
                if (sfUsers != null && !sfUsers.isEmpty()) {
                    log.info("[DATA SOURCE: SALESFORCE] Successfully fetched {} users from Salesforce SOQL (paging in memory)", sfUsers.size());
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
                            page >= totalPages - 1
                    );
                }
            } catch (Exception e) {
                log.warn("[DATA SOURCE: SALESFORCE] Salesforce SOQL read failed for users, falling back to MySQL: {}", e.getMessage());
            }
        }

        log.info("[DATA SOURCE: MYSQL] Fetching paged users (page={}, size={}) from MySQL database", page, size);
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> usersPage = userRepository.findAll(pageable);
        List<UserResponse> content = usersPage.getContent().stream()
                .map(userMapper::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.isLast()
        );
    }

    @Override
    public PagedResponse<UserResponse> searchUsers(String query, int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name()) ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<User> usersPage = userRepository.searchUsers(query, pageable);
        List<UserResponse> content = usersPage.getContent().stream()
                .map(userMapper::mapToResponse)
                .collect(Collectors.toList());

        return new PagedResponse<>(
                content,
                usersPage.getNumber(),
                usersPage.getSize(),
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.isLast()
        );
    }

    @Override
    @Cacheable(value = "users", key = "#p0")
    public UserResponse getUserById(Long id) {
        log.info("CACHE MISS - Fetching user {} from DATABASE", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
        return userMapper.mapToResponse(user);
    }

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

        if (salesforceSyncDelegate != null) {
            try {
                salesforceSyncDelegate.syncUser(updatedUser);
            } catch (Exception e) {
                log.error("Salesforce dual-write failed for user update: {}", e.getMessage());
            }
        }

        return userMapper.mapToResponse(updatedUser);
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#p0")
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with ID: " + id));
        userRepository.delete(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with email: " + email));
        return userMapper.mapToResponse(user);
    }

    @Override
    @Transactional
    public User findOrCreateGoogleUser(String googleId, String email, String name) {
        return userRepository.findByEmail(email)
                .map(existingUser -> {
                    if (existingUser.getProviderId() == null) {
                        existingUser.setProviderId(googleId);
                        existingUser.setProvider(AuthProvider.GOOGLE_OAUTH);
                    }
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .providerId(googleId)
                            .provider(AuthProvider.GOOGLE_OAUTH)
                            .role(RoleEnum.USER)
                            .build();
                    return userRepository.save(newUser);
                });
    }
}
