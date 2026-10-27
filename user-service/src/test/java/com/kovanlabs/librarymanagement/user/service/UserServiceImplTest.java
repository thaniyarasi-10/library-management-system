package com.kovanlabs.librarymanagement.user.service;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
import com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import com.kovanlabs.librarymanagement.user.dto.CompleteProfileRequest;
import com.kovanlabs.librarymanagement.user.dto.UserRequest;
import com.kovanlabs.librarymanagement.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private RewardRepository rewardRepository;

    @Mock
    private FineRepository fineRepository;

    @Mock
    private AadhaarSecurityService aadhaarSecurityService;

    private AccountDeactivationService accountDeactivationService;

    private UserServiceImpl userService;

    private User user1;
    private User user2;
    private String uuid1;
    private String uuid2;

    @BeforeEach
    void setUp() {
        accountDeactivationService = spy(new AccountDeactivationService(userRepository, salesforceSyncDelegate));
        userService = new UserServiceImpl(
                userRepository,
                userProviderRepository,
                rewardRepository,
                fineRepository,
                salesforceSyncDelegate,
                accountDeactivationService,
                aadhaarSecurityService
        );

        uuid1 = UUID.randomUUID().toString();
        uuid2 = UUID.randomUUID().toString();

        user1 = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .build();

        user2 = User.builder()
                .uuid(uuid2)
                .id(2L)
                .name("Bob Jones")
                .email("bob@example.com")
                .build();
    }

    @Mock
    private SalesforceSync salesforceSyncDelegate;

    @Test
    void createUser_shouldSaveAndReturnUserResponse() {
        UserRequest request = new UserRequest("alice@example.com", "Alice Smith", null);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        assertEquals("Alice Smith", response.name());
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void createUser_whenSalesforceSyncSucceeds_shouldMarkStatusSuccess() {
        UserRequest request = new UserRequest("alice@example.com", "Alice Smith", null);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void createUser_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        UserRequest request = new UserRequest("alice@example.com", "Alice Smith", null);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Salesforce down")).when(salesforceSyncDelegate)
                .syncContact(any(ContactSObject.class));

        UserResponse response = userService.createUser(request);

        assertNotNull(response);
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void getAllUsers_shouldReturnList() {
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        List<UserResponse> users = userService.getAllUsers();

        assertEquals(2, users.size());
    }

    @Test
    void getAllUsers_paginated_shouldReturnPagedResponse() {
        Page<User> page = new PageImpl<>(List.of(user1), PageRequest.of(0, 10, Sort.by("id").ascending()), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
    }

    @Test
    @DisplayName("searchUsers should return PagedResponse with matching users")
    void searchUsers_ShouldReturnPagedResponse() {
        List<User> users = List.of(user1);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<User> usersPage = new PageImpl<>(users, pageable, users.size());

        when(userRepository.searchUsers(eq("Alice"), any(Pageable.class))).thenReturn(usersPage);

        PagedResponse<UserResponse> response = userService.searchUsers("Alice", 0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("Alice Smith", response.content().getFirst().name());
    }

    @Test
    void getUserById_whenUserExists_shouldReturnUserResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        UserResponse response = userService.getUserById(1L);

        assertEquals("Alice Smith", response.name());
    }

    @Test
    void getUserById_whenUserNotFound_shouldThrowResponseStatusException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.getUserById(99L));
    }

    @Test
    void updateUser_whenUserExists_shouldUpdateEmailAndSave() {
        UserRequest request = new UserRequest("updated@example.com", "Alice Smith", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("updated@example.com", response.email());
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, user1.getSalesforceSyncStatus());
        assertEquals(0, user1.getSalesforceRetryCount());
    }

    @Test
    void updateUser_whenPriorRetryCountNonZero_shouldResetRetryCountAndSetSuccess() {
        user1.setSalesforceRetryCount(3);
        user1.setSalesforceSyncStatus(SalesforceSyncStatus.FAILED);
        UserRequest request = new UserRequest("updated@example.com", "Alice Smith", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("updated@example.com", response.email());
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, user1.getSalesforceSyncStatus());
        assertEquals(0, user1.getSalesforceRetryCount());
    }

    @Test
    void updateUser_whenUserExists_shouldUpdatePhoneAndSave() {
        UserRequest request = new UserRequest("updated@example.com", "Alice Smith", "+15555555555");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("+15555555555", response.phone());
        assertEquals("+15555555555", user1.getPhone());
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, user1.getSalesforceSyncStatus());
        assertEquals(0, user1.getSalesforceRetryCount());
    }

    @Test
    void updateUser_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        user1.setSalesforceRetryCount(3);
        user1.setSalesforceSyncStatus(SalesforceSyncStatus.FAILED);
        UserRequest request = new UserRequest("updated@example.com", "Alice Smith", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Salesforce update error")).when(salesforceSyncDelegate)
                .syncContact(any(ContactSObject.class));

        UserResponse response = userService.updateUser(1L, request);

        assertEquals("updated@example.com", response.email());
        assertEquals(SalesforceSyncStatus.PENDING, user1.getSalesforceSyncStatus());
        assertEquals(1, user1.getSalesforceRetryCount());
    }

    @Test
    void deleteUser_whenUserExists_shouldDeleteAndSyncSalesforce() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository, times(1)).delete(user1);
        verify(salesforceSyncDelegate, times(1)).deleteUser(uuid1);
    }

    @Test
    void deleteUser_whenUserNotFound_shouldThrowNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.deleteUser(999L));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteUser_whenSalesforceDeleteThrows_shouldLogAndNotFail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        doThrow(new RuntimeException("SF delete error")).when(salesforceSyncDelegate).deleteUser(uuid1);

        assertDoesNotThrow(() -> userService.deleteUser(1L));
        verify(userRepository, times(1)).delete(user1);
    }

    @Test
    void syncAuth0User_whenExistingProviderId_shouldReturnExistingUserWithoutUpdatingUserOrSalesforce() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        UserProvider up = UserProvider.builder()
                .userUuid(testUser.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|12345")
                .build();
        when(userProviderRepository.findByProviderId("auth0|12345")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(testUser.getUuid())).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User("auth0|12345", "different-email@example.com", "Different Name",
                RoleEnum.ADMIN);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        assertEquals(1L, result.getId());
        assertEquals("Alice Smith", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(RoleEnum.USER, result.getRole());
        assertEquals(SalesforceSyncStatus.SUCCESS, result.getSalesforceSyncStatus());
        assertEquals(0, result.getSalesforceRetryCount());
        assertEquals(created, result.getCreatedAt());
        assertEquals(updated, result.getUpdatedAt());

        verify(userRepository, never()).save(any(User.class));
        verify(userProviderRepository, never()).save(any(UserProvider.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenExistingUserWithDifferentProvider_shouldOnlyCreateUserProviderAndKeepUserUntouched() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        // Existing provider is GOOGLE_OAUTH; AUTH0 mapping is not yet in place
        when(userProviderRepository.findByProviderId("auth0|new-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User("auth0|new-sub", "alice@example.com", "Different Name", RoleEnum.ADMIN);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        assertEquals(1L, result.getId());
        assertEquals("Alice Smith", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(RoleEnum.USER, result.getRole());
        assertEquals(SalesforceSyncStatus.SUCCESS, result.getSalesforceSyncStatus());
        assertEquals(0, result.getSalesforceRetryCount());
        assertEquals(created, result.getCreatedAt());
        assertEquals(updated, result.getUpdatedAt());

        verify(userProviderRepository, times(1)).save(argThat(up -> up.getUserUuid().equals(testUser.getUuid())
                && up.getProvider() == AuthProvider.AUTH0
                && "auth0|new-sub".equals(up.getProviderId())));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenExistingUserWithMultipleProviderIds_shouldStoreAllAsSeparateUserProviderRecords() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        when(userProviderRepository.findByProviderId("google-oauth2|123456789")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User("google-oauth2|123456789", "alice@example.com", "Alice Smith",
                RoleEnum.USER);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        verify(userProviderRepository, times(1)).save(argThat(up -> up.getUserUuid().equals(testUser.getUuid())
                && up.getProvider() == AuthProvider.AUTH0
                && "google-oauth2|123456789".equals(up.getProviderId())));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenExistingUserWithGoogleOAuthRawId_shouldLinkAuth0ToExistingUserUuidAndKeepUserUntouched() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 1, 1, 10, 0);
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.ADMIN)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .createdAt(created)
                .updatedAt(updated)
                .build();

        String auth0Sub = "google-oauth2|115993863360458947065";
        String rawGoogleId = "115993863360458947065";

        UserProvider existingGoogleProvider = UserProvider.builder()
                .userUuid(testUser.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId(rawGoogleId)
                .build();

        // Exact sub not found, but raw ID matches existing GOOGLE_OAUTH provider
        when(userProviderRepository.findByProviderId(auth0Sub)).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId(rawGoogleId)).thenReturn(Optional.of(existingGoogleProvider));
        when(userRepository.findByUuid(testUser.getUuid())).thenReturn(Optional.of(testUser));

        User result = userService.syncAuth0User(auth0Sub, "thaniyarasi10@gmail.com", "thani", RoleEnum.USER);

        assertNotNull(result);
        assertEquals(testUser.getUuid(), result.getUuid());
        assertEquals(1L, result.getId());
        assertEquals("Alice Smith", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        assertEquals(RoleEnum.ADMIN, result.getRole());
        assertEquals(SalesforceSyncStatus.SUCCESS, result.getSalesforceSyncStatus());
        assertEquals(0, result.getSalesforceRetryCount());
        assertEquals(created, result.getCreatedAt());
        assertEquals(updated, result.getUpdatedAt());

        // Only the missing AUTH0 mapping is created
        verify(userProviderRepository, times(1)).save(argThat(up -> up.getUserUuid().equals(user1.getUuid())
                && up.getProvider() == AuthProvider.AUTH0
                && auth0Sub.equals(up.getProviderId())));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_whenNoExistingUser_shouldCreateNewUserWithRoleAndSyncSalesforce() {
        when(userProviderRepository.findByProviderId("auth0|brand-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("brandnew@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (Objects.isNull(u.getUuid())) {
                u.setUuid(UUID.randomUUID().toString());
            }
            return u;
        });

        User result = userService.syncAuth0User("auth0|brand-new", "brandnew@example.com", "Brand New", RoleEnum.ADMIN);

        assertEquals("brandnew@example.com", result.getEmail());
        assertEquals(RoleEnum.ADMIN, result.getRole());
        verify(userRepository, atLeastOnce()).save(any(User.class));
        verify(userProviderRepository, times(1)).save(any(UserProvider.class));
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
    }

    @Test
    void syncAuth0User_whenSameSubUsedAgain_shouldNotCreateDuplicateUser() {
        UserProvider up = UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|existing")
                .build();
        when(userProviderRepository.findByProviderId("auth0|existing")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        User result1 = userService.syncAuth0User("auth0|existing", "alice@example.com", "Alice Smith", RoleEnum.USER);
        User result2 = userService.syncAuth0User("auth0|existing", "alice@example.com", "Alice Smith", RoleEnum.USER);

        assertEquals(result1, result2);
        verify(userRepository, never()).save(any(User.class));
        verify(userProviderRepository, never()).save(any(UserProvider.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void syncAuth0User_shouldNeverStoreSubAsEmailOrInUserTable() {
        when(userProviderRepository.findByProviderId("auth0|sub-12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (Objects.isNull(u.getUuid())) {
                u.setUuid(UUID.randomUUID().toString());
            }
            return u;
        });

        User result = userService.syncAuth0User("auth0|sub-12345", "test@example.com", "Test User", RoleEnum.USER);

        assertEquals("test@example.com", result.getEmail());
        assertFalse(result.getEmail().contains("auth0|sub-12345"));
        verify(userProviderRepository).save(argThat(up -> "auth0|sub-12345".equals(up.getProviderId())));
    }

    @Test
    void syncAuth0User_whenSubIsEmpty_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User("", "test@example.com", "Test", RoleEnum.USER));
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User(null, "test@example.com", "Test", RoleEnum.USER));
    }

    @Test
    void syncAuth0User_whenSubHasUnexpectedPipeFormat_shouldFallbackToEmailGracefully() {
        // sub ends with pipe, no rawId
        when(userProviderRepository.findByProviderId("auth0|")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user1));

        User result = userService.syncAuth0User("auth0|", "test@example.com", "Test", RoleEnum.USER);
        assertNotNull(result);
        assertEquals(user1.getUuid(), result.getUuid());
        verify(userProviderRepository).save(argThat(up -> "auth0|".equals(up.getProviderId())));
    }

    @Test
    void syncAuth0User_whenNewUserAndEmailMissing_shouldThrowException() {
        when(userProviderRepository.findByProviderId("auth0|new-no-email")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User("auth0|new-no-email", null, "Test", RoleEnum.USER));
        assertThrows(IllegalArgumentException.class,
                () -> userService.syncAuth0User("auth0|new-no-email", "   ", "Test", RoleEnum.USER));
    }

    @Test
    void updateUserRole_whenUserExists_shouldUpdateRoleAndSyncSalesforce() {
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserRole(1L, RoleEnum.ADMIN);

        assertNotNull(response);
        assertEquals(RoleEnum.ADMIN, testUser.getRole());
        verify(userRepository, atLeastOnce()).save(testUser);
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, testUser.getSalesforceSyncStatus());
        assertEquals(0, testUser.getSalesforceRetryCount());
    }

    @Test
    void updateUserRole_whenPriorRetryCountNonZero_shouldResetRetryCountAndSetSuccess() {
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceRetryCount(3)
                .salesforceSyncStatus(SalesforceSyncStatus.FAILED)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserRole(1L, RoleEnum.ADMIN);

        assertNotNull(response);
        assertEquals(RoleEnum.ADMIN, testUser.getRole());
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
        assertEquals(SalesforceSyncStatus.SUCCESS, testUser.getSalesforceSyncStatus());
        assertEquals(0, testUser.getSalesforceRetryCount());
    }

    @Test
    void updateUserRole_whenUserNotFound_shouldThrowNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.updateUserRole(99L, RoleEnum.ADMIN));
    }

    @Test
    void updateUserRole_whenRoleNull_shouldThrowBadRequest() {
        assertThrows(ResponseStatusException.class, () -> userService.updateUserRole(1L, null));
    }

    @Test
    void updateUserRole_whenSalesforceSyncThrows_shouldMarkPending() {
        User testUser = User.builder()
                .uuid(uuid1)
                .id(1L)
                .name("Alice Smith")
                .email("alice@example.com")
                .role(RoleEnum.USER)
                .salesforceRetryCount(3)
                .salesforceSyncStatus(SalesforceSyncStatus.FAILED)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SF dual-write fail")).when(salesforceSyncDelegate)
                .syncContact(any(ContactSObject.class));

        UserResponse response = userService.updateUserRole(1L, RoleEnum.ADMIN);

        assertNotNull(response);
        assertEquals(SalesforceSyncStatus.PENDING, testUser.getSalesforceSyncStatus());
        assertEquals(1, testUser.getSalesforceRetryCount());
    }

    @Test
    void syncAuth0User_whenNewUserAndSalesforceFails_shouldCatchExceptionAndMarkPending() {
        when(userProviderRepository.findByProviderId("auth0|new-error-sf")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new-sf-error@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("SF sync error")).when(salesforceSyncDelegate)
                .syncContact(any(ContactSObject.class));

        User created = userService.syncAuth0User("auth0|new-error-sf", "new-sf-error@example.com", "New User",
                RoleEnum.USER);

        assertNotNull(created);
        assertEquals(SalesforceSyncStatus.PENDING, created.getSalesforceSyncStatus());
        assertEquals(1, created.getSalesforceRetryCount());
    }

    @Test
    void updateCurrentUser_whenPrincipalNull_shouldThrowUnauthorized() {
        assertThrows(ResponseStatusException.class,
                () -> userService.updateCurrentUser(null, new UserRequest("new@example.com", "New Name", null)));
    }

    @Test
    void updateCurrentUser_whenValidPrincipal_shouldUpdateAndReturnUser() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("alice@example.com");
        when(userProviderRepository.findByProviderId("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateCurrentUser(principal,
                new UserRequest("alice_new@example.com", "Alice Updated", null));

        assertNotNull(response);
        assertEquals("Alice Updated", response.name());
        assertEquals("alice_new@example.com", response.email());
        verify(userRepository, atLeastOnce()).save(user1);
    }

    @Test
    void getUserByIdentifier_whenFoundByProviderId_shouldReturnUser() {
        UserProvider up = UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|alice")
                .build();
        when(userProviderRepository.findByProviderId("auth0|alice")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByIdentifier("auth0|alice");

        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenNotFound_shouldThrowNotFound() {
        when(userProviderRepository.findByProviderId("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.getUserByIdentifier("unknown"));
    }

    @Test
    @DisplayName("getCurrentUser with null principal should throw UNAUTHORIZED")
    void getCurrentUser_whenPrincipalNull_shouldThrowUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> userService.getCurrentUser(null));
    }

    @Test
    @DisplayName("getCurrentUser with standard Principal should lookup by identifier")
    void getCurrentUser_whenStandardPrincipal_shouldLookupByIdentifier() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("alice@example.com");
        when(userProviderRepository.findByProviderId("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getCurrentUser(principal);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    @DisplayName("getCurrentUser with JWT and ADMIN custom claim should sync user with RoleEnum.ADMIN")
    void getCurrentUser_whenJwtWithAdminRole_shouldSyncAdmin() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|admin-1")
                .claim("email", "admin@example.com")
                .claim("name", "Admin User")
                .claim("https://library-management.com/roles", List.of("ADMIN"))
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(userProviderRepository.findByProviderId("auth0|admin-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            u.setUuid(UUID.randomUUID().toString());
            return u;
        });

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        verify(userRepository, atLeastOnce()).save(argThat(user -> user.getRole() == RoleEnum.ADMIN));
    }

    @Test
    @DisplayName("getCurrentUser when JWT lacks email and user not mapped should create new user using email claim")
    void getCurrentUser_whenJwtHasClaims_shouldCreateOrSyncUser() {
        Jwt jwt = Jwt.withTokenValue("valid-access-token-123")
                .header("alg", "none")
                .claim("sub", "google-oauth2|115993863360458947065")
                .claim("email", "thaniyarasi10@gmail.com")
                .claim("name", "Thani Yarasi")
                .claim("iss", "https://dev-etrfpmdm1sjiuggl.us.auth0.com/")
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(userProviderRepository.findByProviderId("google-oauth2|115993863360458947065"))
                .thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("115993863360458947065")).thenReturn(Optional.empty());

        when(userRepository.findByEmail("thaniyarasi10@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(50L);
            u.setUuid(UUID.randomUUID().toString());
            return u;
        });

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        assertEquals("thaniyarasi10@gmail.com", response.email());
        assertEquals("Thani Yarasi", response.name());
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
        verify(userProviderRepository)
                .save(argThat(up -> "google-oauth2|115993863360458947065".equals(up.getProviderId())));
    }

    @Test
    @DisplayName("getCurrentUser when user already mapped in user_provider should not create new user")
    void getCurrentUser_whenUserAlreadyMapped_shouldReturnExistingUser() {
        Jwt jwt = Jwt.withTokenValue("valid-access-token-456")
                .header("alg", "none")
                .claim("sub", "auth0|existing-mapped-sub")
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserProvider up = UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("auth0|existing-mapped-sub")
                .build();
        when(userProviderRepository.findByProviderId("auth0|existing-mapped-sub")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    @DisplayName("getCurrentUser when JWT sub is new but matches existing MySQL user email should link provider without duplicate")
    void getCurrentUser_whenJwtSubIsNewAndMatchesExistingEmail_shouldLinkProviderWithoutDuplicate() {
        Jwt jwt = Jwt.withTokenValue("valid-access-token-789")
                .header("alg", "none")
                .claim("sub", "google-oauth2|new-google-sub")
                .claim("email", "alice@example.com")
                .claim("name", "Alice Google")
                .claim("iss", "https://dev-etrfpmdm1sjiuggl.us.auth0.com/")
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(userProviderRepository.findByProviderId("google-oauth2|new-google-sub")).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("new-google-sub")).thenReturn(Optional.empty());

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
        verify(userProviderRepository).save(argThat(p -> "google-oauth2|new-google-sub".equals(p.getProviderId())
                && user1.getUuid().equals(p.getUserUuid())));
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    void getUserByEmail_shouldDelegateToGetUserByIdentifier() {
        when(userProviderRepository.findByProviderId("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByEmail("alice@example.com");

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenGivenRawProviderIdWithPrefix_shouldFindUser() {
        UserProvider up = UserProvider.builder()
                .userUuid(user1.getUuid())
                .provider(AuthProvider.AUTH0)
                .providerId("115993863360458947065")
                .build();
        when(userProviderRepository.findByProviderId("google-oauth2|115993863360458947065"))
                .thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("115993863360458947065")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(user1.getUuid())).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByIdentifier("google-oauth2|115993863360458947065");

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenGivenUuidString_shouldFindUser() {
        when(userProviderRepository.findByProviderId(uuid1)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(uuid1)).thenReturn(Optional.empty());
        when(userRepository.findByUuid(uuid1)).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(uuid1)).thenReturn(Optional.empty());

        UserResponse response = userService.getUserByIdentifier(uuid1);

        assertNotNull(response);
        assertEquals("alice@example.com", response.email());
    }

    @Test
    void getUserByIdentifier_whenIdentifierBlankOrNull_shouldThrowBadRequest() {
        assertThrows(ResponseStatusException.class, () -> userService.getUserByIdentifier(null));
        assertThrows(ResponseStatusException.class, () -> userService.getUserByIdentifier("   "));
    }

    @Test
    void getAllUsers_whenSalesforceReturnsUsers_shouldReturnSalesforceUsers() {
        ContactSObject sfContact = ContactSObject.builder()
                .externalUserUuid(uuid1)
                .legacyUserId(1L)
                .lastName("Alice SF")
                .email("alice@example.com")
                .role("USER")
                .build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(sfContact));

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(1, result.size());
        assertEquals("Alice SF", result.getFirst().name());
        verify(userRepository, never()).findAll();
    }

    @Test
    void getAllUsers_whenSalesforceFails_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SF error"));
        when(userRepository.findAll()).thenReturn(List.of(user1));

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(1, result.size());
        assertEquals("Alice Smith", result.getFirst().name());
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsers_paginated_whenSalesforceReturnsUsers_shouldReturnPagedFromSF() {
        ContactSObject sfContact = ContactSObject.builder()
                .externalUserUuid(uuid1)
                .legacyUserId(1L)
                .lastName("Alice SF")
                .email("alice@example.com")
                .role("USER")
                .build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(sfContact));

        PagedResponse<UserResponse> result = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("Alice SF", result.content().getFirst().name());
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAllUsers_paginated_whenSalesforceFails_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SF error"));
        Page<User> page = new PageImpl<>(List.of(user1), PageRequest.of(0, 10, Sort.by("id").ascending()), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<UserResponse> result = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals("Alice Smith", result.content().getFirst().name());
        verify(userRepository).findAll(any(Pageable.class));
    }

    // --- Tests for getHeaderFromRequestContext ---

    @Test
    @DisplayName("getHeaderFromRequestContext should read X-User-Username and X-User-Name from RequestContextHolder when present")
    void getHeaderFromRequestContext_shouldExtractHeaderWhenPresent() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-User-Username", "CustomHeaderUser");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(mockRequest));

        try {
            Jwt jwt = mock(Jwt.class);
            when(jwt.getSubject()).thenReturn("auth0|header-test");
            when(jwt.getClaimAsString("email")).thenReturn("header-user@example.com");

            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(jwt);

            when(userProviderRepository.findByProviderId("auth0|header-test")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("header-user@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

            UserResponse response = userService.getCurrentUser(auth);

            assertNotNull(response);
            assertEquals("CustomHeaderUser", response.name());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("getHeaderFromRequestContext should fallback to X-User-Name if X-User-Username is missing")
    void getHeaderFromRequestContext_shouldFallbackToXUserName() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("X-User-Name", "FallbackHeaderName");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(mockRequest));

        try {
            Jwt jwt = mock(Jwt.class);
            when(jwt.getSubject()).thenReturn("auth0|header-fallback");
            when(jwt.getClaimAsString("email")).thenReturn("header-fallback@example.com");

            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(jwt);

            when(userProviderRepository.findByProviderId("auth0|header-fallback")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("header-fallback@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
            when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

            UserResponse response = userService.getCurrentUser(auth);

            assertNotNull(response);
            assertEquals("FallbackHeaderName", response.name());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("getHeaderFromRequestContext should return null safely when RequestContextHolder has no attributes")
    void getHeaderFromRequestContext_whenNoRequestContext_shouldSafelyReturnNull() {
        RequestContextHolder.resetRequestAttributes();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("auth0|no-ctx");
        when(jwt.getClaimAsString("email")).thenReturn("no-ctx@example.com");
        when(jwt.getClaimAsString("name")).thenReturn("JwtName");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(jwt);

        when(userProviderRepository.findByProviderId("auth0|no-ctx")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("no-ctx@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("JwtName", response.name());
    }

    // --- Tests for syncUserToSalesforce ---

    @Test
    @DisplayName("syncUserToSalesforce should execute and mark SUCCESS on new user registration")
    void syncUserToSalesforce_whenNewUserCreated_shouldSyncAndMarkSuccess() {
        when(userProviderRepository.findByProviderId("auth0|sf-success")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("sf-success@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        User created = userService.syncAuth0User("auth0|sf-success", "sf-success@example.com", "SF Success User",
                RoleEnum.USER);

        assertNotNull(created);
        assertEquals(SalesforceSyncStatus.SUCCESS, created.getSalesforceSyncStatus());
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
        verify(userRepository, atLeast(2)).save(any(User.class));
    }

    @Test
    @DisplayName("syncUserToSalesforce should increment retry count and mark PENDING when Salesforce throws exception")
    void syncUserToSalesforce_whenSalesforceThrows_shouldCatchErrorAndMarkPending() {
        when(userProviderRepository.findByProviderId("auth0|sf-fail")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("sf-fail@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Salesforce service connection error")).when(salesforceSyncDelegate)
                .syncContact(any(ContactSObject.class));

        User created = userService.syncAuth0User("auth0|sf-fail", "sf-fail@example.com", "SF Fail User", RoleEnum.USER);

        assertNotNull(created);
        assertEquals(SalesforceSyncStatus.PENDING, created.getSalesforceSyncStatus());
        assertEquals(1, created.getSalesforceRetryCount());
        verify(salesforceSyncDelegate, times(1)).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("syncUserToSalesforce should not invoke Salesforce when salesforceSyncDelegate is null")
    void syncUserToSalesforce_whenDelegateIsNull_shouldDoNothing() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository,
                userProviderRepository,
                rewardRepository,
                fineRepository,
                null,
                accountDeactivationService,
                aadhaarSecurityService);

        when(userProviderRepository.findByProviderId("auth0|no-sf")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("no-sf@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        User created = serviceWithoutSF.syncAuth0User("auth0|no-sf", "no-sf@example.com", "No SF", RoleEnum.USER);

        assertNotNull(created);
        verify(salesforceSyncDelegate, never()).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("syncAuth0User when raw provider exists but user missing in DB should throw 404")
    void syncAuth0User_whenRawProviderFoundButUserMissing_shouldThrowNotFound() {
        String sub = "google-oauth2|999888777";
        UserProvider rawProvider = UserProvider.builder()
                .providerId("999888777")
                .provider(AuthProvider.AUTH0)
                .userUuid("missing-uuid-123")
                .build();

        when(userProviderRepository.findByProviderId(sub)).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("999888777")).thenReturn(Optional.of(rawProvider));
        when(userRepository.findByUuid("missing-uuid-123")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.syncAuth0User(sub, "missing@example.com", "Missing", RoleEnum.USER));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User not found for raw providerId: 999888777"));
    }

    @Test
    @DisplayName("syncAuth0User when sub has multiple pipe characters should parse last raw ID")
    void syncAuth0User_whenSubHasMultiplePipes_shouldTryLastRawId() {
        String sub = "provider|subprefix|unique123";
        UserProvider rawProvider = UserProvider.builder()
                .providerId("unique123")
                .provider(AuthProvider.AUTH0)
                .userUuid(user1.getUuid())
                .build();

        when(userProviderRepository.findByProviderId(sub)).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("subprefix|unique123")).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("unique123")).thenReturn(Optional.of(rawProvider));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        User result = userService.syncAuth0User(sub, "alice@example.com", "Alice Smith", RoleEnum.USER);

        assertNotNull(result);
        assertEquals(user1.getUuid(), result.getUuid());
        verify(userProviderRepository).save(argThat(up -> sub.equals(up.getProviderId())));
    }

    @Test
    @DisplayName("getCurrentUser when JWT role claim specifies USER or ROLE_USER should extract RoleEnum.USER")
    void getCurrentUser_whenJwtHasUserRole_shouldAssignRoleUser() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt-user")
                .header("alg", "none")
                .claim("sub", "auth0|user-role-test")
                .claim("email", "user-role@example.com")
                .claim("name", "Standard User")
                .claim("roles", List.of("ROLE_USER"))
                .build();

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        when(userProviderRepository.findByProviderId("auth0|user-role-test")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user-role@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(20L);
            u.setUuid(UUID.randomUUID().toString());
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.getCurrentUser(jwtAuth);

        assertNotNull(response);
        assertEquals("USER", response.role());
    }

    @Test
    @DisplayName("getAllUsers with duplicate reward records should handle map merge gracefully")
    void getAllUsers_withDuplicateRewardRecords_shouldMergePoints() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SF down"));

        Reward r1 = Reward
                .builder()
                .userUuid(user1.getUuid())
                .points(10)
                .build();
        Reward r2 = Reward
                .builder()
                .userUuid(user1.getUuid())
                .points(20)
                .build();

        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user1)));
        when(rewardRepository.findByUserUuidIn(List.of(user1.getUuid())))
                .thenReturn(List.of(r1, r2));

        PagedResponse<UserResponse> result = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(result);
        assertEquals(1, result.content().size());
        assertEquals(10, result.content().getFirst().rewardPoints());
    }

    @Test
    @DisplayName("syncAuth0User when mapped provider has missing user in DB should throw 404")
    void syncAuth0User_whenUserProviderMappedButUserMissingInDb_shouldThrowNotFound() {
        UserProvider up = UserProvider.builder()
                .providerId("auth0|mapped-but-missing-user")
                .provider(AuthProvider.AUTH0)
                .userUuid("missing-uuid-404")
                .build();

        when(userProviderRepository.findByProviderId("auth0|mapped-but-missing-user")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid("missing-uuid-404")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService
                .syncAuth0User("auth0|mapped-but-missing-user", "test@example.com", "Test", RoleEnum.USER));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User not found for providerId: auth0|mapped-but-missing-user"));
    }

    @Test
    @DisplayName("updateUser when user ID not found should throw 404")
    void updateUser_whenUserNotFound_shouldThrowNotFound() {
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.updateUser(9999L, new UserRequest("test@example.com", "Test", null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertTrue(ex.getReason().contains("User not found with ID: 9999"));
    }

    @Test
    @DisplayName("reactivateUser when user exists should set ACTIVE and APPROVED status")
    void reactivateUser_whenUserExists_shouldSetActive() {
        User user = User.builder()
                .id(100L)
                .uuid("u-100")
                .name("Deactivated Alice")
                .email("alice.deactivated@example.com")
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .inactivityWarningSentAt(LocalDateTime.now().minusDays(17))
                .build();

        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.reactivateUser(100L);

        assertNotNull(response);
        assertEquals(AccountStatus.ACTIVE.name(), response.status());
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertEquals(ReactivationRequestStatus.APPROVED, user.getReactivationRequestStatus());
        assertNull(user.getInactivityWarningSentAt());
        assertNotNull(user.getLastLoginAt());
        verify(userRepository, atLeastOnce()).save(user);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("reactivateUser when user ID not found should throw 404")
    void reactivateUser_whenNotFound_shouldThrow404() {
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.reactivateUser(9999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("deactivateUser when user exists should set DEACTIVATED, reset reward points to 0, and sync to Salesforce")
    void deactivateUser_whenUserExists_shouldSetDeactivated() {
        User user = User.builder()
                .id(200L)
                .uuid("u-200")
                .name("Active Bob")
                .email("bob@example.com")
                .status(AccountStatus.ACTIVE)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .build();

        when(userRepository.findById(200L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(rewardRepository.resetPoints("u-200")).thenReturn(1);

        UserResponse response = userService.deactivateUser(200L);

        assertNotNull(response);
        assertEquals(AccountStatus.DEACTIVATED.name(), response.status());
        assertEquals(AccountStatus.DEACTIVATED, user.getStatus());
        assertNull(user.getReactivationRequestStatus());
        verify(userRepository, atLeastOnce()).save(user);
        verify(rewardRepository).resetPoints("u-200");

        ArgumentCaptor<ContactSObject> captor = ArgumentCaptor.forClass(ContactSObject.class);
        verify(salesforceSyncDelegate).syncContact(captor.capture());
        assertEquals(Integer.valueOf(0), captor.getValue().getReward());
        assertEquals("DEACTIVATED", captor.getValue().getStatus());
    }

    @Test
    @DisplayName("deactivateUser when reward record does not exist should create new reward record with 0 points")
    void deactivateUser_whenRewardRecordDoesNotExist_shouldCreateRewardRecordWithZeroPoints() {
        User user = User.builder()
                .id(201L)
                .uuid("u-201")
                .name("No Reward Bob")
                .email("noreward@example.com")
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findById(201L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(rewardRepository.resetPoints("u-201")).thenReturn(0);

        UserResponse response = userService.deactivateUser(201L);

        assertNotNull(response);
        assertEquals(AccountStatus.DEACTIVATED.name(), response.status());
        verify(rewardRepository).resetPoints("u-201");
        verify(rewardRepository).save(argThat(r -> "u-201".equals(r.getUserUuid()) && Integer.valueOf(0).equals(r.getPoints())));
    }

    @Test
    @DisplayName("deactivateUser when user ID not found should throw 404")
    void deactivateUser_whenNotFound_shouldThrow404() {
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.deactivateUser(9999L));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("syncAuth0User should not modify lastLoginAt or save repository for existing user")
    void syncAuth0User_existingUser_shouldNotUpdateTimestampOrSave() {
        LocalDateTime initialLoginAt = LocalDateTime.now().minusDays(10);
        LocalDateTime warningSentAt = LocalDateTime.now().minusDays(3);
        User existing = User.builder()
                .id(300L)
                .uuid("u-300")
                .name("Returning User")
                .email("return@example.com")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(initialLoginAt)
                .inactivityWarningSentAt(warningSentAt)
                .build();

        UserProvider provider = UserProvider.builder()
                .userUuid("u-300")
                .providerId("auth0|returning")
                .build();

        when(userProviderRepository.findByProviderId("auth0|returning")).thenReturn(Optional.of(provider));
        when(userRepository.findByUuid("u-300")).thenReturn(Optional.of(existing));

        User result = userService.syncAuth0User("auth0|returning", "return@example.com", "Returning User",
                RoleEnum.USER);

        assertNotNull(result);
        assertEquals(initialLoginAt, result.getLastLoginAt());
        assertEquals(warningSentAt, result.getInactivityWarningSentAt());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("requestReactivation should succeed when deactivated and no pending fines")
    void requestReactivation_whenDeactivatedAndNoPendingFines_shouldSucceed() {
        User deactivated = User.builder()
                .id(400L)
                .uuid("u-400")
                .name("Deactivated User")
                .email("deact@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();

        Principal principal = () -> "deact@example.com";
        when(userRepository.findByEmail("deact@example.com")).thenReturn(Optional.of(deactivated));
        when(fineRepository.findByUserUuidAndStatus("u-400", FineStatus.PENDING)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.requestReactivation(principal);

        assertNotNull(res);
        assertTrue(res.reactivationRequested());
        assertEquals("PENDING", res.reactivationRequestStatus());
        assertEquals(ReactivationRequestStatus.PENDING, deactivated.getReactivationRequestStatus());
        assertFalse(res.hasPendingFines());
        assertEquals("DEACTIVATED", res.status());
        verify(userRepository, atLeastOnce()).save(deactivated);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("requestReactivation should throw BAD_REQUEST when already pending")
    void requestReactivation_whenAlreadyPending_shouldThrowBadRequest() {
        User deactivated = User.builder()
                .id(400L)
                .uuid("u-400")
                .name("Pending User")
                .email("pending@example.com")
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .build();

        Principal principal = () -> "pending@example.com";
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(deactivated));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(principal));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already pending"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestReactivation should throw BAD_REQUEST when user has pending fines")
    void requestReactivation_whenHasPendingFines_shouldThrowBadRequest() {
        User deactivated = User.builder()
                .id(401L)
                .uuid("u-401")
                .name("Fines User")
                .email("fines@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();

        Fine pendingFine = Fine.builder()
                .id(10L)
                .userUuid("u-401")
                .pendingFineAmount(new BigDecimal("15.50"))
                .status(FineStatus.PENDING)
                .build();

        Principal principal = () -> "fines@example.com";
        when(userRepository.findByEmail("fines@example.com")).thenReturn(Optional.of(deactivated));
        when(fineRepository.findByUserUuidAndStatus("u-401", FineStatus.PENDING)).thenReturn(List.of(pendingFine));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(principal));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("pending fines"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("requestReactivation should throw BAD_REQUEST when user is not deactivated")
    void requestReactivation_whenUserIsActive_shouldThrowBadRequest() {
        User active = User.builder()
                .id(402L)
                .uuid("u-402")
                .email("active@example.com")
                .status(AccountStatus.ACTIVE)
                .build();

        Principal principal = () -> "active@example.com";
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(active));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(principal));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Account is not deactivated"));
    }

    @Test
    @DisplayName("getReactivationRequests from Salesforce primary source")
    void getReactivationRequests_fromSalesforcePrimarySource() {
        ContactSObject contact = ContactSObject.builder()
                .externalUserUuid("u-500")
                .legacyUserId(500L)
                .lastName("SF User")
                .email("sf@example.com")
                .status("DEACTIVATED")
                .reactivationRequestStatus("PENDING")
                .build();

        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(contact));

        List<UserResponse> list = userService.getReactivationRequests();

        assertEquals(1, list.size());
        assertEquals("sf@example.com", list.getFirst().email());
        assertEquals("PENDING", list.getFirst().reactivationRequestStatus());
        assertTrue(list.getFirst().reactivationRequested());
    }

    @Test
    @DisplayName("getReactivationRequests fallback to MySQL when Salesforce returns empty or throws")
    void getReactivationRequests_fallbackToMySql() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of());

        User reqUser = User.builder()
                .id(500L)
                .uuid("u-500")
                .email("req@example.com")
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .build();

        when(userRepository.findByReactivationRequestStatusIsNotNull())
                .thenReturn(List.of(reqUser));

        List<UserResponse> list = userService.getReactivationRequests();

        assertEquals(1, list.size());
        assertEquals("req@example.com", list.getFirst().email());
        assertTrue(list.getFirst().reactivationRequested());
    }

    @Test
    @DisplayName("reactivateUser should reset status to ACTIVE and approve request")
    void reactivateUser_shouldResetStatusAndFlags() {
        User deact = User.builder()
                .id(600L)
                .uuid("u-600")
                .email("reactivate@example.com")
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .build();

        when(userRepository.findById(600L)).thenReturn(Optional.of(deact));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.reactivateUser(600L);

        assertEquals("ACTIVE", res.status());
        assertEquals(ReactivationRequestStatus.APPROVED, deact.getReactivationRequestStatus());
        assertNotNull(deact.getLastLoginAt());
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("rejectReactivation when user exists should set REJECTED and keep DEACTIVATED status")
    void rejectReactivation_whenUserExists_shouldClearFlagAndKeepDeactivated() {
        User deact = User.builder()
                .id(601L)
                .uuid("u-601")
                .email("reject@example.com")
                .status(AccountStatus.DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .build();

        when(userRepository.findById(601L)).thenReturn(Optional.of(deact));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.rejectReactivation(601L);

        assertEquals("DEACTIVATED", res.status());
        assertEquals(ReactivationRequestStatus.REJECTED, deact.getReactivationRequestStatus());
        verify(userRepository, atLeastOnce()).save(deact);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("reactivateUser when user was PERMANENTLY_DEACTIVATED but had PENDING request should succeed and reactivate to ACTIVE")
    void reactivateUser_whenUserWasPermanentlyDeactivatedButPendingRequest_shouldReactivateSuccessfully() {
        User user = User.builder()
                .id(602L)
                .uuid("u-602")
                .email("thaniyarasik@gmail.com")
                .status(AccountStatus.PERMANENTLY_DEACTIVATED)
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .build();

        when(userRepository.findById(602L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.reactivateUser(602L);

        assertNotNull(response);
        assertEquals(AccountStatus.ACTIVE.name(), response.status());
        assertEquals(AccountStatus.ACTIVE, user.getStatus());
        assertEquals(ReactivationRequestStatus.APPROVED, user.getReactivationRequestStatus());
        assertNull(user.getDeactivatedAt());
        verify(userRepository, atLeastOnce()).save(user);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("rejectReactivation when 10-day window expired should transition to PERMANENTLY_DEACTIVATED and REJECTED")
    void rejectReactivation_whenWindowExpired_shouldTransitionToPermanentlyDeactivated() {
        User deact = User.builder()
                .id(603L)
                .uuid("u-603")
                .email("expired-reject@example.com")
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .reactivationRequestStatus(ReactivationRequestStatus.PENDING)
                .build();

        when(userRepository.findById(603L)).thenReturn(Optional.of(deact));
        when(accountDeactivationService.isReactivationWindowExpired(deact)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.rejectReactivation(603L);

        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED.name(), res.status());
        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED, deact.getStatus());
        assertEquals(ReactivationRequestStatus.REJECTED, deact.getReactivationRequestStatus());
        verify(userRepository, atLeastOnce()).save(deact);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("getReactivationRequests should reconcile MySQL users when Salesforce has PENDING request")
    void getReactivationRequests_shouldReconcileMySqlUsersWhenSalesforceHasPendingRequest() {
        ContactSObject contact = ContactSObject.builder()
                .legacyUserId(4L)
                .externalUserUuid("u-4")
                .email("thaniyarasik@gmail.com")
                .status("DEACTIVATED")
                .reactivationRequestStatus("PENDING")
                .build();

        User mySqlUser = User.builder()
                .id(4L)
                .uuid("u-4")
                .email("thaniyarasik@gmail.com")
                .status(AccountStatus.PERMANENTLY_DEACTIVATED)
                .reactivationRequestStatus(null)
                .build();

        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(contact));
        when(userRepository.findById(4L)).thenReturn(Optional.of(mySqlUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        List<UserResponse> result = userService.getReactivationRequests();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(AccountStatus.DEACTIVATED, mySqlUser.getStatus());
        assertEquals(ReactivationRequestStatus.PENDING, mySqlUser.getReactivationRequestStatus());
        verify(userRepository).save(mySqlUser);
    }

    @Test
    @DisplayName("requestReactivation should throw BAD_REQUEST when user is PERMANENTLY_DEACTIVATED")
    void requestReactivation_whenPermanentlyDeactivated_shouldThrowBadRequest() {
        User user = User.builder()
                .id(403L)
                .uuid("u-403")
                .email("perm@example.com")
                .status(AccountStatus.PERMANENTLY_DEACTIVATED)
                .build();

        Principal principal = () -> "perm@example.com";
        when(userRepository.findByEmail("perm@example.com")).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(principal));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("permanently deactivated"));
        verify(userRepository, never()).save(any());
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    @DisplayName("requestReactivation when deactivated > 10 days should update to PERMANENTLY_DEACTIVATED, sync Salesforce, and throw BAD_REQUEST")
    void requestReactivation_whenDeactivatedExceeds10Days_shouldTransitionToPermanentlyDeactivatedAndThrow() {
        User user = User.builder()
                .id(404L)
                .uuid("u-404")
                .email("expired@example.com")
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(11))
                .build();

        Principal principal = () -> "expired@example.com";
        when(userRepository.findByEmail("expired@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(principal));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("expired"));
        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED, user.getStatus());
        assertNull(user.getReactivationRequestStatus());
        verify(userRepository, atLeastOnce()).save(user);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("requestReactivation when deactivated within 10 days should succeed")
    void requestReactivation_whenDeactivatedWithin10Days_shouldSucceed() {
        User user = User.builder()
                .id(405L)
                .uuid("u-405")
                .email("within10@example.com")
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(5))
                .build();

        Principal principal = () -> "within10@example.com";
        when(userRepository.findByEmail("within10@example.com")).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuidAndStatus("u-405", FineStatus.PENDING)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.requestReactivation(principal);

        assertNotNull(res);
        assertTrue(res.reactivationRequested());
        assertEquals("PENDING", res.reactivationRequestStatus());
        assertEquals(ReactivationRequestStatus.PENDING, user.getReactivationRequestStatus());
        assertEquals("DEACTIVATED", res.status());
        verify(userRepository, atLeastOnce()).save(user);
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("reactivateUser when user is PERMANENTLY_DEACTIVATED should throw BAD_REQUEST")
    void reactivateUser_whenPermanentlyDeactivated_shouldThrowBadRequest() {
        User user = User.builder()
                .id(406L)
                .uuid("u-406")
                .email("perm2@example.com")
                .status(AccountStatus.PERMANENTLY_DEACTIVATED)
                .build();

        when(userRepository.findById(406L)).thenReturn(Optional.of(user));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.reactivateUser(406L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("permanently deactivated"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateUser should set deactivatedAt timestamp")
    void deactivateUser_shouldSetDeactivatedAt() {
        User user = User.builder()
                .id(407L)
                .uuid("u-407")
                .email("deact-date@example.com")
                .status(AccountStatus.ACTIVE)
                .build();

        when(userRepository.findById(407L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.deactivateUser(407L);

        assertEquals("DEACTIVATED", res.status());
        assertNotNull(user.getDeactivatedAt());
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    @DisplayName("processExpiredDeactivations should permanently deactivate users exceeding 10 days and sync to Salesforce")
    void processExpiredDeactivations_shouldPermanentlyDeactivateUsersAndSyncToSalesforce() {
        User expiredUser1 = User.builder()
                .id(701L)
                .uuid("u-701")
                .email("exp1@example.com")
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(15))
                .build();

        User expiredUser2 = User.builder()
                .id(702L)
                .uuid("u-702")
                .email("exp2@example.com")
                .status(AccountStatus.DEACTIVATED)
                .deactivatedAt(LocalDateTime.now().minusDays(12))
                .build();

        when(userRepository.findUsersEligibleForPermanentDeactivation(eq(AccountStatus.DEACTIVATED), any(LocalDateTime.class)))
                .thenReturn(List.of(expiredUser1, expiredUser2));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        int count = userService.processExpiredDeactivations();

        assertEquals(2, count);
        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED, expiredUser1.getStatus());
        assertEquals(AccountStatus.PERMANENTLY_DEACTIVATED, expiredUser2.getStatus());
        verify(userRepository, times(2)).save(expiredUser1);
        verify(userRepository, times(2)).save(expiredUser2);
        verify(salesforceSyncDelegate, times(2)).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("processExpiredDeactivations when none found should return 0")
    void processExpiredDeactivations_whenNoneFound_shouldReturnZero() {
        when(userRepository.findUsersEligibleForPermanentDeactivation(eq(AccountStatus.DEACTIVATED), any(LocalDateTime.class)))
                .thenReturn(List.of());

        int count = userService.processExpiredDeactivations();

        assertEquals(0, count);
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    @DisplayName("rejectReactivation when user ID not found should throw 404")
    void rejectReactivation_whenNotFound_shouldThrow404() {
        when(userRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> userService.rejectReactivation(9999L));
    }

    @Test
    @DisplayName("recordLogin for existing user with warning should update lastLoginAt and clear warning timestamp")
    void recordLogin_existingUserWithWarning_shouldUpdateLastLoginAndClearWarning() {
        LocalDateTime oldLogin = LocalDateTime.now().minusDays(25);
        User user = User.builder()
                .id(700L)
                .uuid("u-700")
                .name("Returning User")
                .email("returning@example.com")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(oldLogin)
                .inactivityWarningSentAt(LocalDateTime.now().minusDays(2))
                .build();

        UserProvider up = UserProvider.builder()
                .userUuid("u-700")
                .providerId("auth0|returning-700")
                .build();

        when(userProviderRepository.findByProviderId("auth0|returning-700")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid("u-700")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|returning-700")
                .claim("email", "returning@example.com")
                .claim("name", "Returning User")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse res = userService.recordLogin(auth);

        assertNotNull(res);
        assertNull(user.getInactivityWarningSentAt());
        assertNotNull(user.getLastLoginAt());
        assertTrue(user.getLastLoginAt().isAfter(oldLogin));
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    @DisplayName("recordLogin with Google OAuth sub should update lastLoginAt")
    void recordLogin_googleOAuthUser_shouldUpdateLastLogin() {
        LocalDateTime oldLogin = LocalDateTime.now().minusDays(15);
        User user = User.builder()
                .id(701L)
                .uuid("u-701")
                .name("Google User")
                .email("google@example.com")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(oldLogin)
                .build();

        UserProvider up = UserProvider.builder()
                .userUuid("u-701")
                .providerId("google-oauth2|11223344")
                .build();

        when(userProviderRepository.findByProviderId("google-oauth2|11223344")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid("u-701")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "google-oauth2|11223344")
                .claim("email", "google@example.com")
                .claim("name", "Google User")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse res = userService.recordLogin(auth);

        assertNotNull(res);
        assertNotNull(user.getLastLoginAt());
        assertTrue(user.getLastLoginAt().isAfter(oldLogin));
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    @DisplayName("recordLogin with username/password Auth0 sub should update lastLoginAt")
    void recordLogin_usernamePasswordUser_shouldUpdateLastLogin() {
        LocalDateTime oldLogin = LocalDateTime.now().minusDays(5);
        User user = User.builder()
                .id(702L)
                .uuid("u-702")
                .name("Db User")
                .email("dbuser@example.com")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(oldLogin)
                .build();

        UserProvider up = UserProvider.builder()
                .userUuid("u-702")
                .providerId("auth0|db-pass-123")
                .build();

        when(userProviderRepository.findByProviderId("auth0|db-pass-123")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid("u-702")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|db-pass-123")
                .claim("email", "dbuser@example.com")
                .claim("name", "Db User")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse res = userService.recordLogin(auth);

        assertNotNull(res);
        assertNotNull(user.getLastLoginAt());
        assertTrue(user.getLastLoginAt().isAfter(oldLogin));
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    @DisplayName("recordLogin for brand new user should initialize lastLoginAt timestamp")
    void recordLogin_newUser_shouldReceiveInitialLoginTimestamp() {
        when(userProviderRepository.findByProviderId("auth0|brand-new")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(703L);
            u.setUuid("u-703");
            return u;
        });

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|brand-new")
                .claim("email", "new@example.com")
                .claim("name", "New User")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse res = userService.recordLogin(auth);

        assertNotNull(res);
        verify(userRepository, atLeastOnce()).save(argThat(u -> Objects.nonNull(u.getLastLoginAt())));
    }

    @Test
    @DisplayName("Calling getCurrentUser repeatedly should not modify lastLoginAt or trigger repository save")
    void getCurrentUser_repeatedCalls_shouldNotModifyLastLoginAt() {
        LocalDateTime originalLoginAt = LocalDateTime.now().minusDays(12);
        User user = User.builder()
                .id(704L)
                .uuid("u-704")
                .name("Repeat User")
                .email("repeat@example.com")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(originalLoginAt)
                .build();

        UserProvider up = UserProvider.builder()
                .userUuid("u-704")
                .providerId("auth0|repeat-704")
                .build();

        when(userProviderRepository.findByProviderId("auth0|repeat-704")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid("u-704")).thenReturn(Optional.of(user));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|repeat-704")
                .claim("email", "repeat@example.com")
                .claim("name", "Repeat User")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // First call
        UserResponse res1 = userService.getCurrentUser(auth);
        assertNotNull(res1);

        // Second call
        UserResponse res2 = userService.getCurrentUser(auth);
        assertNotNull(res2);

        // Third call
        UserResponse res3 = userService.getCurrentUser(auth);
        assertNotNull(res3);

        assertEquals(originalLoginAt, user.getLastLoginAt());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("recordLogin with null principal should throw UNAUTHORIZED")
    void recordLogin_nullPrincipal_shouldThrowUnauthorized() {
        assertThrows(ResponseStatusException.class, () -> userService.recordLogin(null));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("recordLogin with standard Principal should resolve user and update lastLoginAt")
    void recordLogin_standardPrincipal_shouldUpdateLastLogin() {
        LocalDateTime oldLogin = LocalDateTime.now().minusDays(8);
        User user = User.builder()
                .id(705L)
                .uuid("u-705")
                .name("Std User")
                .email("std@example.com")
                .status(AccountStatus.ACTIVE)
                .lastLoginAt(oldLogin)
                .build();

        Principal stdPrincipal = () -> "std@example.com";
        when(userProviderRepository.findByProviderId("std@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("std@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse res = userService.recordLogin(stdPrincipal);

        assertNotNull(res);
        assertNotNull(user.getLastLoginAt());
        assertTrue(user.getLastLoginAt().isAfter(oldLogin));
        verify(userRepository, atLeastOnce()).save(user);
    }

    // =========================================================================
    // Targeted Branch Coverage Tests
    // =========================================================================

    // --- toUserResponse & toUserResponseList branch tests ---

    @Test
    @DisplayName("createUser when mapped entity is null should return null from toUserResponse")
    void createUser_whenEntityIsNull_shouldReturnNullFromToUserResponse() {
        when(userRepository.save(null)).thenReturn(null);

        UserResponse response = userService.createUser(null);

        assertNull(response);
    }

    @Test
    @DisplayName("getUserById when user has null UUID should return response with zero reward points and zero fine amount")
    void getUserById_whenUserUuidIsNull_shouldReturnZeroPointsAndFines() {
        User userWithoutUuid = User.builder()
                .id(990L)
                .uuid(null)
                .name("No UUID User")
                .email("nouuid@example.com")
                .build();
        when(userRepository.findById(990L)).thenReturn(Optional.of(userWithoutUuid));

        UserResponse response = userService.getUserById(990L);

        assertNotNull(response);
        assertEquals(0, response.rewardPoints());
        assertFalse(response.hasPendingFines());
        assertEquals(0.0, response.pendingFineAmount());
        verifyNoInteractions(rewardRepository);
        verifyNoInteractions(fineRepository);
    }

    @Test
    @DisplayName("getUserById when user has pending fine with null amount should filter null amount and return zero fine amount")
    void getUserById_whenFineHasNullPendingAmount_shouldFilterAndReturnZero() {
        Fine fineWithNullAmount = Fine.builder()
                .id(88L)
                .userUuid(uuid1)
                .status(FineStatus.PENDING)
                .pendingFineAmount(null)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(uuid1)).thenReturn(Optional.empty());
        when(fineRepository.findByUserUuidAndStatus(uuid1, FineStatus.PENDING))
                .thenReturn(List.of(fineWithNullAmount));

        UserResponse response = userService.getUserById(1L);

        assertNotNull(response);
        assertFalse(response.hasPendingFines());
        assertEquals(0.0, response.pendingFineAmount());
    }

    @Test
    @DisplayName("getUserById when user has pending fine with positive amount should return hasPendingFines true")
    void getUserById_whenFineHasPositivePendingAmount_shouldReturnHasPendingFinesTrue() {
        Fine fineWithAmount = Fine.builder()
                .id(89L)
                .userUuid(uuid1)
                .status(FineStatus.PENDING)
                .pendingFineAmount(new BigDecimal("12.50"))
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(rewardRepository.findByUserUuid(uuid1)).thenReturn(Optional.empty());
        when(fineRepository.findByUserUuidAndStatus(uuid1, FineStatus.PENDING))
                .thenReturn(List.of(fineWithAmount));

        UserResponse response = userService.getUserById(1L);

        assertNotNull(response);
        assertTrue(response.hasPendingFines());
        assertEquals(12.50, response.pendingFineAmount());
    }

    @Test
    @DisplayName("getAllUsers paginated when database returns empty page should return empty content")
    void getAllUsers_paginated_whenDatabaseEmpty_shouldReturnEmptyList() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository, userProviderRepository, rewardRepository, fineRepository, null, accountDeactivationService, aadhaarSecurityService);
        Page<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        PagedResponse<UserResponse> response = serviceWithoutSF.getAllUsers(0, 10, "id", "asc");

        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
    }

    @Test
    @DisplayName("getAllUsers when database returns user with null UUID should filter out null UUID in toUserResponseList")
    void getAllUsers_whenUsersContainNullUuid_shouldFilterNullUuid() {
        User userWithNullUuid = User.builder().id(3L).uuid(null).name("Null UUID").email("nulluuid@example.com").build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SF error"));
        when(userRepository.findAll()).thenReturn(List.of(user1, userWithNullUuid));
        when(rewardRepository.findByUserUuidIn(List.of(uuid1))).thenReturn(List.of());

        List<UserResponse> list = userService.getAllUsers();

        assertNotNull(list);
        assertEquals(2, list.size());
        verify(rewardRepository).findByUserUuidIn(List.of(uuid1));
    }

    // --- getAllUsers & searchUsers branches ---

    @Test
    @DisplayName("getAllUsers without Salesforce delegate should fetch directly from MySQL database")
    void getAllUsers_whenSalesforceDelegateNull_shouldFetchFromMySQL() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository, userProviderRepository, rewardRepository, fineRepository, null, accountDeactivationService, aadhaarSecurityService);
        when(userRepository.findAll()).thenReturn(List.of(user1));
        when(rewardRepository.findByUserUuidIn(List.of(uuid1))).thenReturn(List.of());

        List<UserResponse> result = serviceWithoutSF.getAllUsers();

        assertEquals(1, result.size());
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("getAllUsers when Salesforce returns null should fallback to MySQL database")
    void getAllUsers_whenSalesforceReturnsNull_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(null);
        when(userRepository.findAll()).thenReturn(List.of(user1));
        when(rewardRepository.findByUserUuidIn(List.of(uuid1))).thenReturn(List.of());

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(1, result.size());
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("getAllUsers when Salesforce returns empty list should fallback to MySQL database")
    void getAllUsers_whenSalesforceReturnsEmptyList_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(user1));
        when(rewardRepository.findByUserUuidIn(List.of(uuid1))).thenReturn(List.of());

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(1, result.size());
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("getAllUsers paginated with sortDir desc on MySQL fallback should apply descending sort")
    void getAllUsers_paginated_withSortDirDesc_shouldApplyDescendingSort() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SF unavailable"));
        Page<User> page = new PageImpl<>(List.of(user1), PageRequest.of(0, 10, Sort.by("email").descending()), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(rewardRepository.findByUserUuidIn(List.of(uuid1))).thenReturn(List.of());

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 10, "email", "desc");

        assertNotNull(response);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAll(pageableCaptor.capture());
        assertEquals(Sort.Direction.DESC, pageableCaptor.getValue().getSort().getOrderFor("email").getDirection());
    }

    @Test
    @DisplayName("getAllUsers paginated from Salesforce when requested page is beyond total should return empty pageContent")
    void getAllUsers_paginated_salesforce_whenPageBeyondTotal_shouldReturnEmptyPageContent() {
        ContactSObject contact = ContactSObject.builder().externalUserUuid(uuid1).lastName("Alice SF").email("alice@sf.com").build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(contact));

        PagedResponse<UserResponse> response = userService.getAllUsers(5, 10, "id", "asc");

        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        assertEquals(1, response.totalElements());
        assertEquals(1, response.totalPages());
        assertTrue(response.last());
    }

    @Test
    @DisplayName("getAllUsers paginated from Salesforce when last page has partial size should slice properly")
    void getAllUsers_paginated_salesforce_whenLastPagePartialSize_shouldSliceCorrectly() {
        ContactSObject c1 = ContactSObject.builder().externalUserUuid("u-1").lastName("User 1").email("u1@sf.com").build();
        ContactSObject c2 = ContactSObject.builder().externalUserUuid("u-2").lastName("User 2").email("u2@sf.com").build();
        ContactSObject c3 = ContactSObject.builder().externalUserUuid("u-3").lastName("User 3").email("u3@sf.com").build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(c1, c2, c3));

        PagedResponse<UserResponse> response = userService.getAllUsers(1, 2, "id", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("User 3", response.content().getFirst().name());
        assertEquals(3, response.totalElements());
        assertEquals(2, response.totalPages());
        assertTrue(response.last());
    }

    @Test
    @DisplayName("getAllUsers paginated when Salesforce returns null should fallback to MySQL")
    void getAllUsers_paginated_whenSalesforceReturnsNull_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(null);
        Page<User> page = new PageImpl<>(List.of(user1), PageRequest.of(0, 10, Sort.by("id").ascending()), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(response);
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("getAllUsers paginated when Salesforce returns empty list should fallback to MySQL")
    void getAllUsers_paginated_whenSalesforceReturnsEmpty_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of());
        Page<User> page = new PageImpl<>(List.of(user1), PageRequest.of(0, 10, Sort.by("id").ascending()), 1);
        when(userRepository.findAll(any(Pageable.class))).thenReturn(page);

        PagedResponse<UserResponse> response = userService.getAllUsers(0, 10, "id", "asc");

        assertNotNull(response);
        verify(userRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("searchUsers with sortDir desc should apply descending sort order")
    void searchUsers_withSortDirDesc_shouldApplyDescendingSort() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("name").descending());
        Page<User> usersPage = new PageImpl<>(List.of(user1), pageable, 1);
        when(userRepository.searchUsers(eq("Alice"), any(Pageable.class))).thenReturn(usersPage);

        PagedResponse<UserResponse> response = userService.searchUsers("Alice", 0, 10, "name", "desc");

        assertNotNull(response);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchUsers(eq("Alice"), pageableCaptor.capture());
        assertEquals(Sort.Direction.DESC, pageableCaptor.getValue().getSort().getOrderFor("name").getDirection());
    }

    // --- updateUser branches ---

    @Test
    @DisplayName("updateUser with blank name and blank email should preserve existing user name and email")
    void updateUser_whenNameAndEmailAreBlank_shouldNotModifyNameOrEmail() {
        User existing = User.builder().id(1L).uuid(uuid1).name("Original Name").email("orig@example.com").phone("12345").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserRequest request = new UserRequest("   ", "   ", null);
        UserResponse response = userService.updateUser(1L, request);

        assertNotNull(response);
        assertEquals("Original Name", existing.getName());
        assertEquals("orig@example.com", existing.getEmail());
        assertEquals("12345", existing.getPhone());
    }

    @Test
    @DisplayName("updateUser with blank phone should set phone to null")
    void updateUser_whenPhoneIsBlank_shouldSetPhoneToNull() {
        User existing = User.builder().id(1L).uuid(uuid1).name("Original Name").email("orig@example.com").phone("12345").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserRequest request = new UserRequest("orig@example.com", "Original Name", "   ");
        UserResponse response = userService.updateUser(1L, request);

        assertNotNull(response);
        assertNull(existing.getPhone());
    }

    @Test
    @DisplayName("updateUser with untrimmed phone should trim and update phone")
    void updateUser_whenPhoneHasWhitespace_shouldTrimPhone() {
        User existing = User.builder().id(1L).uuid(uuid1).name("Original Name").email("orig@example.com").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserRequest request = new UserRequest("orig@example.com", "Original Name", "  +19876543210  ");
        UserResponse response = userService.updateUser(1L, request);

        assertNotNull(response);
        assertEquals("+19876543210", existing.getPhone());
    }

    @Test
    @DisplayName("updateUser when salesforceSyncDelegate is null should update without attempting Salesforce sync")
    void updateUser_whenSalesforceDelegateNull_shouldUpdateWithoutSync() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository, userProviderRepository, rewardRepository, fineRepository, null, accountDeactivationService, aadhaarSecurityService);
        User existing = User.builder().id(1L).uuid(uuid1).name("Original Name").email("orig@example.com").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserRequest request = new UserRequest("new@example.com", "New Name", null);
        UserResponse response = serviceWithoutSF.updateUser(1L, request);

        assertNotNull(response);
        assertEquals("New Name", existing.getName());
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    // --- deleteUser branches ---

    @Test
    @DisplayName("deleteUser when salesforceSyncDelegate is null should delete from MySQL without throwing")
    void deleteUser_whenSalesforceDelegateNull_shouldDeleteFromMySQL() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository, userProviderRepository, rewardRepository, fineRepository, null, accountDeactivationService, aadhaarSecurityService);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));

        assertDoesNotThrow(() -> serviceWithoutSF.deleteUser(1L));
        verify(userRepository).delete(user1);
        verify(salesforceSyncDelegate, never()).deleteUser(any());
    }

    @Test
    @DisplayName("deleteUser when user uuid is null should delete from MySQL and skip Salesforce delete")
    void deleteUser_whenUserUuidIsNull_shouldSkipSalesforceDelete() {
        User userWithoutUuid = User.builder().id(2L).uuid(null).email("no-uuid@example.com").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithoutUuid));

        assertDoesNotThrow(() -> userService.deleteUser(2L));
        verify(userRepository).delete(userWithoutUuid);
        verify(salesforceSyncDelegate, never()).deleteUser(any());
    }

    // --- findUserEntityByIdentifier & getUserByIdentifier branches ---

    @Test
    @DisplayName("getUserByIdentifier when identifier has pipe but blank rawId should skip raw lookup and find by email")
    void getUserByIdentifier_whenPipeHasBlankRawId_shouldSkipRawLookupAndFind() {
        when(userProviderRepository.findByProviderId("auth0|")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("auth0|")).thenReturn(Optional.of(user1));

        UserResponse response = userService.getUserByIdentifier("auth0|");

        assertNotNull(response);
        assertEquals("Alice Smith", response.name());
    }

    @Test
    @DisplayName("getUserByIdentifier when not found by provider or email should find by UUID")
    void getUserByIdentifier_whenNotFoundByEmail_shouldFindByUuid() {
        String uuidStr = UUID.randomUUID().toString();
        when(userProviderRepository.findByProviderId(uuidStr)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(uuidStr)).thenReturn(Optional.empty());
        when(userRepository.findByUuid(uuidStr)).thenReturn(Optional.of(user1));

        UserResponse response = userService.getUserByIdentifier(uuidStr);

        assertNotNull(response);
        assertEquals(user1.getEmail(), response.email());
    }

    @Test
    @DisplayName("getUserByIdentifier when not found anywhere should throw 404")
    void getUserByIdentifier_whenNotFoundAnywhere_shouldThrow404() {
        when(userProviderRepository.findByProviderId("unknown-id")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown-id")).thenReturn(Optional.empty());
        when(userRepository.findByUuid("unknown-id")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.getUserByIdentifier("unknown-id"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- updateUserRole branches ---

    @Test
    @DisplayName("updateUserRole when salesforceSyncDelegate is null should update role in MySQL without error")
    void updateUserRole_whenSalesforceDelegateNull_shouldUpdateRoleInMySQL() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository, userProviderRepository, rewardRepository, fineRepository, null, accountDeactivationService, aadhaarSecurityService);
        User user = User.builder().id(1L).uuid(uuid1).email("test@example.com").role(RoleEnum.USER).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = serviceWithoutSF.updateUserRole(1L, RoleEnum.ADMIN);

        assertNotNull(response);
        assertEquals(RoleEnum.ADMIN, user.getRole());
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    // --- syncAuth0User branches ---

    @Test
    @DisplayName("syncAuth0User when raw provider matches and sub mapping already exists should not re-save provider")
    void syncAuth0User_whenRawProviderFoundAndSubAlreadyMapped_shouldNotSaveNewProvider() {
        String sub = "google-oauth2|12345";
        UserProvider rawProvider = UserProvider.builder().providerId("12345").provider(AuthProvider.AUTH0).userUuid(user1.getUuid()).build();
        UserProvider subProvider = UserProvider.builder().providerId(sub).provider(AuthProvider.AUTH0).userUuid(user1.getUuid()).build();

        when(userProviderRepository.findByProviderId(sub))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(subProvider));
        when(userProviderRepository.findByProviderId("12345")).thenReturn(Optional.of(rawProvider));
        when(userRepository.findByUuid(user1.getUuid())).thenReturn(Optional.of(user1));

        User result = userService.syncAuth0User(sub, "alice@example.com", "Alice Smith", RoleEnum.USER);

        assertNotNull(result);
        verify(userProviderRepository, never()).save(any(UserProvider.class));
    }

    @Test
    @DisplayName("syncAuth0User when matching email found and sub mapping already exists should not re-save provider")
    void syncAuth0User_whenEmailFoundAndSubAlreadyMapped_shouldNotSaveNewProvider() {
        String sub = "saml|org|998877";
        UserProvider subProvider = UserProvider.builder().providerId(sub).provider(AuthProvider.AUTH0).userUuid(user1.getUuid()).build();

        when(userProviderRepository.findByProviderId(sub))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(subProvider));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));

        User result = userService.syncAuth0User(sub, "alice@example.com", "Alice Smith", RoleEnum.USER);

        assertNotNull(result);
        verify(userProviderRepository, never()).save(any(UserProvider.class));
    }

    @Test
    @DisplayName("syncAuth0User when tokenRole is null and name is null should default role to USER and name to User")
    void syncAuth0User_whenTokenRoleAndNameNull_shouldApplyDefaults() {
        when(userProviderRepository.findByProviderId("auth0|new-default")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("default@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(995L);
            u.setUuid("u-995");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.syncAuth0User("auth0|new-default", "default@example.com", null, null);

        assertNotNull(result);
        assertEquals(RoleEnum.USER, result.getRole());
        assertEquals("User", result.getName());
        verify(userRepository, atLeastOnce()).save(argThat(u -> "User".equals(u.getName()) && u.getRole() == RoleEnum.USER));
    }

    @Test
    @DisplayName("syncAuth0User when name is blank should default name to User")
    void syncAuth0User_whenNameIsBlank_shouldDefaultNameToUser() {
        when(userProviderRepository.findByProviderId("auth0|blank-name")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("blankname@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(996L);
            u.setUuid("u-996");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.syncAuth0User("auth0|blank-name", "blankname@example.com", "   ", RoleEnum.ADMIN);

        assertNotNull(result);
        assertEquals("User", result.getName());
        assertEquals(RoleEnum.ADMIN, result.getRole());
    }

    @Test
    @DisplayName("syncAuth0User when sub ends in pipe should handle empty rawId and fallback to creation")
    void syncAuth0User_whenSubEndsInPipe_shouldHandleEmptyRawIdAndCreateUser() {
        when(userProviderRepository.findByProviderId("pipe-end|")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("pipeend@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(997L);
            u.setUuid("u-997");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.syncAuth0User("pipe-end|", "pipeend@example.com", "Pipe End", RoleEnum.USER);

        assertNotNull(result);
        assertEquals("Pipe End", result.getName());
    }

    // --- recordLogin & getCurrentUser branches ---

    @Test
    @DisplayName("recordLogin when user status is DEACTIVATED should not update lastLoginAt or sync to Salesforce")
    void recordLogin_whenUserIsDeactivated_shouldNotUpdateLastLoginOrSync() {
        User deactivated = User.builder()
                .id(801L)
                .uuid("u-801")
                .name("Deactivated Member")
                .email("deact-member@example.com")
                .status(AccountStatus.DEACTIVATED)
                .lastLoginAt(LocalDateTime.now().minusDays(40))
                .build();

        Principal principal = () -> "deact-member@example.com";
        when(userProviderRepository.findByProviderId("deact-member@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("deact-member@example.com")).thenReturn(Optional.of(deactivated));

        UserResponse response = userService.recordLogin(principal);

        assertNotNull(response);
        assertEquals("DEACTIVATED", response.status());
        verify(userRepository, never()).save(any(User.class));
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    @DisplayName("recordLogin with JWT when standard email claims missing should use fallback email claim")
    void recordLogin_whenFallbackEmailClaimPresent_shouldResolveUser() {
        User user = User.builder().id(802L).uuid("u-802").name("Fallback Email User").email("fb@example.com").status(AccountStatus.ACTIVE).build();
        when(userProviderRepository.findByProviderId("auth0|fb-email")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("fb@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|fb-email")
                .claim("https://library-management.com/email", "fb@example.com")
                .claim("name", "Fallback Email User")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.recordLogin(auth);

        assertNotNull(response);
        assertEquals("fb@example.com", response.email());
    }

    @Test
    @DisplayName("recordLogin with JWT when name claim is missing should use nickname claim")
    void recordLogin_whenNameMissing_shouldUseNicknameClaim() {
        User user = User.builder().id(803L).uuid("u-803").name("NickUser").email("nick@example.com").status(AccountStatus.ACTIVE).build();
        when(userProviderRepository.findByProviderId("auth0|nick-user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nick@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|nick-user")
                .claim("email", "nick@example.com")
                .claim("nickname", "NickUser")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.recordLogin(auth);

        assertNotNull(response);
        assertEquals("nick@example.com", response.email());
    }

    @Test
    @DisplayName("getCurrentUser with JWT when name and nickname missing should use namespaced name claim")
    void getCurrentUser_whenNameAndNicknameMissing_shouldUseNamespacedNameClaim() {
        when(userProviderRepository.findByProviderId("auth0|custom-namespace-name")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nsname@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(804L);
            u.setUuid("u-804");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|custom-namespace-name")
                .claim("email", "nsname@example.com")
                .claim("https://library-management.com/name", "Namespaced Name")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("Namespaced Name", response.name());
    }

    @Test
    @DisplayName("getCurrentUser with JWT when all name claims missing should default name to User")
    void getCurrentUser_whenAllNameClaimsMissing_shouldDefaultNameToUser() {
        when(userProviderRepository.findByProviderId("auth0|noname")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("noname@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(805L);
            u.setUuid("u-805");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|noname")
                .claim("email", "noname@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("User", response.name());
    }

    @Test
    @DisplayName("getCurrentUser with JWT when standard email is blank should use namespaced email claim")
    void getCurrentUser_whenStandardEmailBlank_shouldUseNamespacedEmail() {
        User user = User.builder().id(806L).uuid("u-806").name("NS Email").email("nsemail@example.com").status(AccountStatus.ACTIVE).build();
        when(userProviderRepository.findByProviderId("auth0|nsemail")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nsemail@example.com")).thenReturn(Optional.of(user));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|nsemail")
                .claim("email", "   ")
                .claim("https://library-management.com/email", "nsemail@example.com")
                .claim("name", "NS Email")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("nsemail@example.com", response.email());
    }

    @Test
    @DisplayName("getCurrentUser with JWT when standard email is empty string should use namespaced email claim")
    void getCurrentUser_whenStandardEmailEmptyString_shouldUseManagementEmail() {
        User user = User.builder().id(807L).uuid("u-807").name("Mgmt Email").email("mgmt@example.com").status(AccountStatus.ACTIVE).build();
        when(userProviderRepository.findByProviderId("auth0|mgmt-email")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("mgmt@example.com")).thenReturn(Optional.of(user));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|mgmt-email")
                .claim("email", "")
                .claim("https://library-management.com/email", "mgmt@example.com")
                .claim("name", "Mgmt Email")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("mgmt@example.com", response.email());
    }

    // --- extractRoleFromJwt & extractRolesList branches ---

    @Test
    @DisplayName("extractRoleFromJwt when role is a single String should extract RoleEnum properly")
    void extractRoleFromJwt_whenRoleIsSingleString_shouldExtractRole() {
        when(userProviderRepository.findByProviderId("auth0|single-role")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("singlerole@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(810L);
            u.setUuid("u-810");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|single-role")
                .claim("email", "singlerole@example.com")
                .claim("name", "Single Role")
                .claim("role", "ROLE_ADMIN")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("ADMIN", response.role());
    }

    @Test
    @DisplayName("extractRolesList when list contains non-string elements should filter them safely")
    void extractRolesList_whenListContainsNonStringElements_shouldFilterSafely() {
        when(userProviderRepository.findByProviderId("auth0|mixed-roles")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("mixed@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(811L);
            u.setUuid("u-811");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|mixed-roles")
                .claim("email", "mixed@example.com")
                .claim("roles", List.of(12345, true, "ROLE_USER"))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("USER", response.role());
    }

    @Test
    @DisplayName("extractRolesList when claim is blank string or non-string object should return empty list")
    void extractRolesList_whenClaimIsBlankOrNonString_shouldReturnEmptyList() {
        when(userProviderRepository.findByProviderId("auth0|blank-role")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("blankrole@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(812L);
            u.setUuid("u-812");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|blank-role")
                .claim("email", "blankrole@example.com")
                .claim("role", "   ")
                .claim("permissions", 9999)
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("USER", response.role());
    }

    @Test
    @DisplayName("extractRoleFromJwt when app_metadata map contains role should extract ADMIN")
    void extractRoleFromJwt_whenAppMetadataMapContainsRole_shouldExtractRole() {
        when(userProviderRepository.findByProviderId("auth0|appmeta-admin")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("appmeta@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(813L);
            u.setUuid("u-813");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|appmeta-admin")
                .claim("email", "appmeta@example.com")
                .claim("app_metadata", Map.of("role", "SUPER_ADMIN"))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("ADMIN", response.role());
    }

    @Test
    @DisplayName("extractRoleFromJwt when claim contains BOOKS:WRITE should resolve to RoleEnum.ADMIN")
    void extractRoleFromJwt_whenBooksWritePermission_shouldResolveToAdmin() {
        when(userProviderRepository.findByProviderId("auth0|books-write")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("bookswrite@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(814L);
            u.setUuid("u-814");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|books-write")
                .claim("email", "bookswrite@example.com")
                .claim("app_metadata", Map.of("roles", List.of("BOOKS:WRITE")))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("ADMIN", response.role());
    }

    @Test
    @DisplayName("extractRoleFromJwt when role contains USER substring should resolve to RoleEnum.USER")
    void extractRoleFromJwt_whenPortalUserRole_shouldResolveToUser() {
        when(userProviderRepository.findByProviderId("auth0|portal-user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("portal@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(815L);
            u.setUuid("u-815");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|portal-user")
                .claim("email", "portal@example.com")
                .claim("role", "PORTAL_USER")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("USER", response.role());
    }

    @Test
    @DisplayName("extractRoleFromJwt when role is unrecognized should return null role which defaults to USER")
    void extractRoleFromJwt_whenRoleUnrecognized_shouldReturnNullAndDefaultToUser() {
        when(userProviderRepository.findByProviderId("auth0|guest-user")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("guest@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(816L);
            u.setUuid("u-816");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|guest-user")
                .claim("email", "guest@example.com")
                .claim("role", "GUEST")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("USER", response.role());
    }

    @Test
    @DisplayName("extractRoleFromJwt when app_metadata is not a map should not throw and continue extraction")
    void extractRoleFromJwt_whenAppMetadataNotMap_shouldIgnoreGracefully() {
        when(userProviderRepository.findByProviderId("auth0|meta-string")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("metastring@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(817L);
            u.setUuid("u-817");
            return u;
        });
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|meta-string")
                .claim("email", "metastring@example.com")
                .claim("app_metadata", "simple-string-not-map")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.getCurrentUser(auth);

        assertNotNull(response);
        assertEquals("USER", response.role());
    }

    // --- requestReactivation branches ---

    @Test
    @DisplayName("requestReactivation with null principal should throw UNAUTHORIZED")
    void requestReactivation_whenPrincipalNull_shouldThrowUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("requestReactivation with JWT when provider user missing should fallback to email lookup")
    void requestReactivation_jwt_providerUserMissing_shouldFallbackToEmail() {
        User deactivated = User.builder()
                .id(820L)
                .uuid("u-820")
                .name("Deactivated Falling to Email")
                .email("deact-fallback@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();

        UserProvider up = UserProvider.builder().providerId("auth0|d-missing-uuid").userUuid("non-existent-uuid").build();
        when(userProviderRepository.findByProviderId("auth0|d-missing-uuid")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid("non-existent-uuid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("deact-fallback@example.com")).thenReturn(Optional.of(deactivated));
        when(fineRepository.findByUserUuidAndStatus("u-820", FineStatus.PENDING)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|d-missing-uuid")
                .claim("email", "deact-fallback@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.requestReactivation(auth);

        assertNotNull(response);
        assertEquals("PENDING", response.reactivationRequestStatus());
    }

    @Test
    @DisplayName("requestReactivation with JWT when standard email is blank should use namespaced email claim")
    void requestReactivation_jwt_whenStandardEmailBlank_shouldUseNamespacedEmail() {
        User deactivated = User.builder()
                .id(821L)
                .uuid("u-821")
                .name("Deactivated NS")
                .email("ns-deact@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();

        when(userProviderRepository.findByProviderId("auth0|d-ns")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ns-deact@example.com")).thenReturn(Optional.of(deactivated));
        when(fineRepository.findByUserUuidAndStatus("u-821", FineStatus.PENDING)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|d-ns")
                .claim("email", "")
                .claim("https://library-management.com/email", "ns-deact@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        UserResponse response = userService.requestReactivation(auth);

        assertNotNull(response);
        assertEquals("PENDING", response.reactivationRequestStatus());
    }

    @Test
    @DisplayName("requestReactivation with JWT when user cannot be found by provider or email should throw 404")
    void requestReactivation_jwt_whenUserNotFound_shouldThrowNotFound() {
        when(userProviderRepository.findByProviderId("auth0|unknown-deact")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown-deact@example.com")).thenReturn(Optional.empty());

        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "none")
                .claim("sub", "auth0|unknown-deact")
                .claim("email", "unknown-deact@example.com")
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(auth));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("requestReactivation with standard Principal when email not found should find by UUID")
    void requestReactivation_standardPrincipal_whenEmailNotFound_shouldFindByUuid() {
        User deactivated = User.builder()
                .id(822L)
                .uuid("u-822")
                .name("Deactivated By UUID")
                .email("deact-uuid@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();

        Principal principal = () -> "u-822";
        when(userRepository.findByEmail("u-822")).thenReturn(Optional.empty());
        when(userRepository.findByUuid("u-822")).thenReturn(Optional.of(deactivated));
        when(fineRepository.findByUserUuidAndStatus("u-822", FineStatus.PENDING)).thenReturn(List.of());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.requestReactivation(principal);

        assertNotNull(response);
        assertEquals("PENDING", response.reactivationRequestStatus());
    }

    @Test
    @DisplayName("requestReactivation with standard Principal when user not found by email or UUID should throw 404")
    void requestReactivation_standardPrincipal_whenNotFound_shouldThrowNotFound() {
        Principal principal = () -> "unknown-user";
        when(userRepository.findByEmail("unknown-user")).thenReturn(Optional.empty());
        when(userRepository.findByUuid("unknown-user")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.requestReactivation(principal));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("requestReactivation when deactivated user has null UUID should skip fine lookup and succeed")
    void requestReactivation_whenUserUuidIsNull_shouldSkipFineLookupAndSucceed() {
        User deactivatedWithoutUuid = User.builder()
                .id(823L)
                .uuid(null)
                .name("No UUID Deactivated")
                .email("nouuid-deact@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();

        Principal principal = () -> "nouuid-deact@example.com";
        when(userRepository.findByEmail("nouuid-deact@example.com")).thenReturn(Optional.of(deactivatedWithoutUuid));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.requestReactivation(principal);

        assertNotNull(response);
        assertEquals("PENDING", response.reactivationRequestStatus());
        verifyNoInteractions(fineRepository);
    }

    @Test
    @DisplayName("requestReactivation when pending fines contain null amounts should filter them and succeed")
    void requestReactivation_whenFineHasNullAmount_shouldFilterAndSucceed() {
        User deactivated = User.builder()
                .id(824L)
                .uuid("u-824")
                .email("nullfine-deact@example.com")
                .status(AccountStatus.DEACTIVATED)
                .build();

        Fine fineWithNullAmount = Fine.builder()
                .id(50L)
                .userUuid("u-824")
                .status(FineStatus.PENDING)
                .pendingFineAmount(null)
                .build();

        Principal principal = () -> "nullfine-deact@example.com";
        when(userRepository.findByEmail("nullfine-deact@example.com")).thenReturn(Optional.of(deactivated));
        when(fineRepository.findByUserUuidAndStatus("u-824", FineStatus.PENDING)).thenReturn(List.of(fineWithNullAmount));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.requestReactivation(principal);

        assertNotNull(response);
        assertEquals("PENDING", response.reactivationRequestStatus());
    }

    // --- getReactivationRequests branches ---

    @Test
    @DisplayName("getReactivationRequests when salesforceSyncDelegate is null should fetch from MySQL")
    void getReactivationRequests_whenSalesforceDelegateNull_shouldFetchFromMySQL() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository, userProviderRepository, rewardRepository, fineRepository, null, accountDeactivationService, aadhaarSecurityService);
        User user = User.builder().id(830L).uuid("u-830").email("req-nosf@example.com")
                .status(AccountStatus.DEACTIVATED).reactivationRequestStatus(ReactivationRequestStatus.PENDING).build();
        when(userRepository.findByReactivationRequestStatusIsNotNull()).thenReturn(List.of(user));

        List<UserResponse> list = serviceWithoutSF.getReactivationRequests();

        assertEquals(1, list.size());
        assertEquals("req-nosf@example.com", list.getFirst().email());
        verify(userRepository).findByReactivationRequestStatusIsNotNull();
    }

    @Test
    @DisplayName("getReactivationRequests from Salesforce should filter out contacts with null or blank request status")
    void getReactivationRequests_fromSalesforce_shouldFilterNullAndBlankStatus() {
        ContactSObject c1 = ContactSObject.builder().externalUserUuid("u-1").email("pending@sf.com").reactivationRequestStatus("PENDING").build();
        ContactSObject c2 = ContactSObject.builder().externalUserUuid("u-2").email("null@sf.com").reactivationRequestStatus(null).build();
        ContactSObject c3 = ContactSObject.builder().externalUserUuid("u-3").email("blank@sf.com").reactivationRequestStatus("   ").build();
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenReturn(List.of(c1, c2, c3));

        List<UserResponse> list = userService.getReactivationRequests();

        assertEquals(1, list.size());
        assertEquals("pending@sf.com", list.getFirst().email());
        verify(userRepository, never()).findByReactivationRequestStatusIsNotNull();
    }

    @Test
    @DisplayName("getReactivationRequests when Salesforce throws exception should fallback to MySQL")
    void getReactivationRequests_whenSalesforceThrows_shouldFallbackToMySQL() {
        when(salesforceSyncDelegate.fetchContactsFromSalesforce()).thenThrow(new RuntimeException("SOQL error"));
        User user = User.builder().id(831L).uuid("u-831").email("req-error@example.com")
                .status(AccountStatus.DEACTIVATED).reactivationRequestStatus(ReactivationRequestStatus.PENDING).build();
        when(userRepository.findByReactivationRequestStatusIsNotNull()).thenReturn(List.of(user));

        List<UserResponse> list = userService.getReactivationRequests();

        assertEquals(1, list.size());
        assertEquals("req-error@example.com", list.getFirst().email());
        verify(userRepository).findByReactivationRequestStatusIsNotNull();
    }

    // --- updateCurrentUser branches ---

    @Test
    @DisplayName("updateCurrentUser when current user profile id is null should throw 404")
    void updateCurrentUser_whenCurrentUserProfileIdIsNull_shouldThrowNotFound() {
        Principal principal = () -> "alice@example.com";
        User userWithoutId = User.builder().id(null).uuid(uuid1).email("alice@example.com").build();
        when(userProviderRepository.findByProviderId("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(userWithoutId));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.updateCurrentUser(principal, new UserRequest("new@example.com", "New", null)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    // --- getHeaderFromRequestContext branches ---

    @Test
    @DisplayName("getHeaderFromRequestContext when attributes not ServletRequestAttributes should return null")
    void getHeaderFromRequestContext_whenNotServletRequestAttributes_shouldReturnNull() {
        org.springframework.web.context.request.RequestAttributes nonServletAttributes =
                mock(org.springframework.web.context.request.RequestAttributes.class);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(nonServletAttributes);

        try {
            when(userProviderRepository.findByProviderId("auth0|non-servlet-ctx")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("nonservlet@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(840L);
                u.setUuid("u-840");
                return u;
            });
            when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

            Jwt jwt = Jwt.withTokenValue("mock-jwt")
                    .header("alg", "none")
                    .claim("sub", "auth0|non-servlet-ctx")
                    .claim("email", "nonservlet@example.com")
                    .claim("name", "Fallback Jwt Name")
                    .build();
            JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

            UserResponse response = userService.getCurrentUser(auth);

            assertNotNull(response);
            assertEquals("Fallback Jwt Name", response.name());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("getHeaderFromRequestContext when ServletRequestAttributes has null request should return null")
    void getHeaderFromRequestContext_whenRequestIsNull_shouldReturnNull() {
        org.springframework.web.context.request.ServletRequestAttributes nullRequestAttributes =
                mock(org.springframework.web.context.request.ServletRequestAttributes.class);
        when(nullRequestAttributes.getRequest()).thenReturn(null);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(nullRequestAttributes);

        try {
            when(userProviderRepository.findByProviderId("auth0|null-req-ctx")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("nullreq@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(841L);
                u.setUuid("u-841");
                return u;
            });
            when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

            Jwt jwt = Jwt.withTokenValue("mock-jwt")
                    .header("alg", "none")
                    .claim("sub", "auth0|null-req-ctx")
                    .claim("email", "nullreq@example.com")
                    .claim("name", "Fallback Name Null Req")
                    .build();
            JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

            UserResponse response = userService.getCurrentUser(auth);

            assertNotNull(response);
            assertEquals("Fallback Name Null Req", response.name());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("getHeaderFromRequestContext when getHeader throws exception should catch and return null")
    void getHeaderFromRequestContext_whenHeaderThrows_shouldCatchAndReturnNull() {
        jakarta.servlet.http.HttpServletRequest throwingRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        when(throwingRequest.getHeader(anyString())).thenThrow(new RuntimeException("Header extraction error"));
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(throwingRequest));

        try {
            when(userProviderRepository.findByProviderId("auth0|header-ex-ctx")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("headerex@example.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(842L);
                u.setUuid("u-842");
                return u;
            });
            when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(i -> i.getArgument(0));

            Jwt jwt = Jwt.withTokenValue("mock-jwt")
                    .header("alg", "none")
                    .claim("sub", "auth0|header-ex-ctx")
                    .claim("email", "headerex@example.com")
                    .claim("name", "Fallback After Exception")
                    .build();
            JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));

            UserResponse response = userService.getCurrentUser(auth);

            assertNotNull(response);
            assertEquals("Fallback After Exception", response.name());
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    // --- resetUserRewardPoints & syncUserToSalesforce branches ---

    @Test
    @DisplayName("deactivateUser when user UUID is null should skip reward point reset and Salesforce sync")
    void deactivateUser_whenUserUuidIsNull_shouldSkipRewardResetAndSalesforceSync() {
        User userWithoutUuid = User.builder().id(850L).uuid(null).email("deact-nouuid@example.com").status(AccountStatus.ACTIVE).build();
        when(userRepository.findById(850L)).thenReturn(Optional.of(userWithoutUuid));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.deactivateUser(850L);

        assertNotNull(response);
        assertEquals("DEACTIVATED", response.status());
        verifyNoInteractions(rewardRepository);
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    @DisplayName("deactivateUser when rewardRepository is null should not fail")
    void deactivateUser_whenRewardRepositoryIsNull_shouldNotFail() {
        UserServiceImpl serviceWithoutRewardRepo = new UserServiceImpl(
                userRepository, userProviderRepository, null, fineRepository, salesforceSyncDelegate, accountDeactivationService, aadhaarSecurityService);
        User user = User.builder().id(851L).uuid("u-851").email("no-reward-repo@example.com").status(AccountStatus.ACTIVE).build();
        when(userRepository.findById(851L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = serviceWithoutRewardRepo.deactivateUser(851L);

        assertNotNull(response);
        assertEquals("DEACTIVATED", response.status());
        verify(salesforceSyncDelegate).syncContact(any(ContactSObject.class));
    }

    @Test
    @DisplayName("reactivateUser when salesforceSyncDelegate is null should update in MySQL without error")
    void reactivateUser_whenSalesforceDelegateNull_shouldUpdateInMySQL() {
        UserServiceImpl serviceWithoutSF = new UserServiceImpl(
                userRepository, userProviderRepository, rewardRepository, fineRepository, null, accountDeactivationService, aadhaarSecurityService);
        User user = User.builder().id(852L).uuid("u-852").email("react-nosf@example.com")
                .status(AccountStatus.DEACTIVATED).reactivationRequestStatus(ReactivationRequestStatus.PENDING).build();
        when(userRepository.findById(852L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = serviceWithoutSF.reactivateUser(852L);

        assertNotNull(response);
        assertEquals("ACTIVE", response.status());
        assertEquals(ReactivationRequestStatus.APPROVED, user.getReactivationRequestStatus());
        verify(salesforceSyncDelegate, never()).syncContact(any());
    }

    @Test
    @DisplayName("reactivateUser when Salesforce sync throws exception should increment retry count and mark PENDING")
    void reactivateUser_whenSalesforceThrows_shouldIncrementRetryAndMarkPending() {
        User user = User.builder().id(853L).uuid("u-853").email("react-sf-fail@example.com")
                .status(AccountStatus.DEACTIVATED).reactivationRequestStatus(ReactivationRequestStatus.PENDING).build();
        when(userRepository.findById(853L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Salesforce down")).when(salesforceSyncDelegate).syncContact(any(ContactSObject.class));

        UserResponse response = userService.reactivateUser(853L);

        assertNotNull(response);
        assertEquals("ACTIVE", response.status());
        assertEquals(SalesforceSyncStatus.PENDING, user.getSalesforceSyncStatus());
        assertEquals(1, user.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("rejectReactivation when Salesforce sync throws exception should increment retry count and mark PENDING")
    void rejectReactivation_whenSalesforceThrows_shouldIncrementRetryAndMarkPending() {
        User user = User.builder().id(854L).uuid("u-854").email("reject-sf-fail@example.com")
                .status(AccountStatus.DEACTIVATED).reactivationRequestStatus(ReactivationRequestStatus.PENDING).build();
        when(userRepository.findById(854L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("Salesforce down")).when(salesforceSyncDelegate).syncContact(any(ContactSObject.class));

        UserResponse response = userService.rejectReactivation(854L);

        assertNotNull(response);
        assertEquals("DEACTIVATED", response.status());
        assertEquals(SalesforceSyncStatus.PENDING, user.getSalesforceSyncStatus());
        assertEquals(1, user.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("completeProfile should successfully save username, last4, and sha hash")
    void completeProfile_success() {
        Principal principal = () -> "alice@example.com";
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));
        when(userRepository.findByAadhaarHash("sha256-hash-1234")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        when(aadhaarSecurityService.validateAndNormalize("1234 5678 9012")).thenReturn("123456789012");
        when(aadhaarSecurityService.extractLast4("123456789012")).thenReturn("9012");
        when(aadhaarSecurityService.computeHash("123456789012")).thenReturn("sha256-hash-1234");

        CompleteProfileRequest req =
                new CompleteProfileRequest("AliceUpdated", "1234 5678 9012", "+919876543210");

        UserResponse res = userService.completeProfile(principal, req);

        assertNotNull(res);
        assertEquals("AliceUpdated", res.name());
        assertEquals("9012", res.aadhaarLast4());
        assertEquals("sha256-hash-1234", res.hashAadhar());
        assertTrue(res.isProfileComplete());
        assertEquals("+919876543210", res.phone());

        assertEquals("AliceUpdated", user1.getName());
        assertEquals("9012", user1.getAadhaarLast4());
        assertEquals("sha256-hash-1234", user1.getAadhaarHash());
        assertEquals("+919876543210", user1.getPhone());
    }

    @Test
    @DisplayName("completeProfile with duplicate/conflicting Aadhaar hash should throw CONFLICT")
    void completeProfile_conflictingAadhaar_throwsConflict() {
        Principal principal = () -> "alice@example.com";
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));

        when(aadhaarSecurityService.validateAndNormalize("1234 5678 9012")).thenReturn("123456789012");
        when(aadhaarSecurityService.extractLast4("123456789012")).thenReturn("9012");
        when(aadhaarSecurityService.computeHash("123456789012")).thenReturn("sha256-hash-1234");
        when(userRepository.findByAadhaarHash("sha256-hash-1234")).thenReturn(Optional.of(user2));

        CompleteProfileRequest req =
                new CompleteProfileRequest("AliceUpdated", "1234 5678 9012", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.completeProfile(principal, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("already registered"));
    }

    @Test
    @DisplayName("completeProfile for deactivated user should throw FORBIDDEN")
    void completeProfile_deactivatedUser_throwsForbidden() {
        Principal principal = () -> "alice@example.com";
        user1.setStatus(AccountStatus.DEACTIVATED);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));

        CompleteProfileRequest req =
                new CompleteProfileRequest("AliceUpdated", "1234 5678 9012", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.completeProfile(principal, req));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("completeProfile with blank username should throw BAD_REQUEST")
    void completeProfile_blankUsername_throwsBadRequest() {
        Principal principal = () -> "alice@example.com";
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user1));

        CompleteProfileRequest req =
                new CompleteProfileRequest("   ", "1234 5678 9012", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.completeProfile(principal, req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("completeProfile with null principal should throw UNAUTHORIZED")
    void completeProfile_nullPrincipal_throwsUnauthorized() {
        CompleteProfileRequest req =
                new CompleteProfileRequest("Alice", "1234 5678 9012", null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.completeProfile(null, req));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("completeProfile with null request should throw BAD_REQUEST")
    void completeProfile_nullRequest_throwsBadRequest() {
        Principal principal = () -> "alice@example.com";

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.completeProfile(principal, null));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
