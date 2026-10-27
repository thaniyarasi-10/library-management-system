package com.kovanlabs.librarymanagement.payment.service;

import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserPaymentProfile;
import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.repository.UserPaymentProfileRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileRequestDto;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPaymentProfileServiceImplTest {

    @Mock
    private UserPaymentProfileRepository paymentProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @InjectMocks
    private UserPaymentProfileServiceImpl service;

    private User mockUser;
    private final String email = "john.doe@example.com";
    private final String userUuid = "user-uuid-111";

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .uuid(userUuid)
                .email(email)
                .name("John Doe")
                .build();
    }

    private void mockFindUser() {
        when(userProviderRepository.findByProviderId(email)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));
    }

    @Test
    @DisplayName("getProfile returns DTO when user and profile exist")
    void testGetProfile_exists() {
        mockFindUser();

        UserPaymentProfile profile = UserPaymentProfile.builder()
                .uuid("profile-123")
                .userUuid(userUuid)
                .bankName("HDFC Bank")
                .accountNumber("987654321012")
                .ifscCode("HDFC0001234")
                .accountHolderName("John Doe")
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(profile));

        UserPaymentProfileResponseDto result = service.getProfile(email);

        assertNotNull(result);
        assertEquals("HDFC Bank", result.bankName());
        assertEquals("HDFC0001234", result.ifscCode());
        assertEquals("John Doe", result.accountHolderName());
        assertTrue(result.maskedAccountNumber().endsWith("1012"));
    }

    @Test
    @DisplayName("getProfile returns null when profile does not exist")
    void testGetProfile_notExists() {
        mockFindUser();
        when(paymentProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.empty());

        UserPaymentProfileResponseDto result = service.getProfile(email);

        assertNull(result);
    }

    @Test
    @DisplayName("getProfile throws exception when user is not found")
    void testGetProfile_userNotFound() {
        when(userProviderRepository.findByProviderId(email)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getProfile(email));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("getProfile throws exception when identifier is blank")
    void testGetProfile_blankIdentifier() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getProfile(""));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("getProfile throws exception when identifier is null")
    void testGetProfile_nullIdentifier() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getProfile(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when request is null")
    void testSaveOrUpdate_nullRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(null, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when account holder name is blank")
    void testSaveOrUpdate_blankHolderName() {
        mockFindUser();
        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "HDFC Bank", "123456789012", "HDFC0001234", "   ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(req, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when IFSC is invalid")
    void testSaveOrUpdate_invalidIfsc() {
        mockFindUser();
        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "HDFC Bank", "123456789012", "INVALID_IFSC", "John Doe");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(req, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when IFSC is null")
    void testSaveOrUpdate_nullIfsc() {
        mockFindUser();
        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "HDFC Bank", "123456789012", null, "John Doe");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(req, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when account number is invalid")
    void testSaveOrUpdate_invalidAccount() {
        mockFindUser();
        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "HDFC Bank", "123", "HDFC0001234", "John Doe");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(req, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when account number is null")
    void testSaveOrUpdate_nullAccount() {
        mockFindUser();
        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "HDFC Bank", null, "HDFC0001234", "John Doe");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(req, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile creates new bank profile successfully")
    void testSaveOrUpdate_newProfile() {
        mockFindUser();
        when(paymentProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.empty());

        UserPaymentProfile savedProfile = UserPaymentProfile.builder()
                .uuid("new-profile-uuid")
                .userUuid(userUuid)
                .bankName("State Bank of India")
                .accountNumber("123456789012")
                .ifscCode("SBIN0001234")
                .accountHolderName("Jane Doe")
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentProfileRepository.save(any(UserPaymentProfile.class))).thenReturn(savedProfile);

        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "State Bank of India",
                "123456789012",
                "sbin0001234",
                "Jane Doe"
        );

        UserPaymentProfileResponseDto result = service.saveOrUpdateProfile(req, email);

        assertNotNull(result);
        assertEquals("Jane Doe", result.accountHolderName());
        assertEquals("SBIN0001234", result.ifscCode());
        assertEquals("State Bank of India", result.bankName());
    }

    @Test
    @DisplayName("saveOrUpdateProfile updates existing bank profile")
    void testSaveOrUpdate_updateExisting() {
        mockFindUser();

        UserPaymentProfile existing = UserPaymentProfile.builder()
                .uuid("existing-uuid")
                .userUuid(userUuid)
                .bankName("Old Bank")
                .accountNumber("111122223333")
                .ifscCode("OLDB0001234")
                .accountHolderName("Existing Name")
                .build();

        when(paymentProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(existing));
        when(paymentProfileRepository.save(existing)).thenAnswer(inv -> {
            existing.setUpdatedAt(LocalDateTime.now());
            return existing;
        });

        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "New Bank",
                "999988887777",
                "NEWB0001234",
                "Updated Name"
        );

        UserPaymentProfileResponseDto result = service.saveOrUpdateProfile(req, email);

        assertNotNull(result);
        assertEquals("New Bank", result.bankName());
        assertEquals("Updated Name", result.accountHolderName());
    }

    @Test
    @DisplayName("deleteProfile deletes existing profile")
    void testDeleteProfile_exists() {
        mockFindUser();
        UserPaymentProfile existing = UserPaymentProfile.builder()
                .uuid("existing-uuid")
                .userUuid(userUuid)
                .build();

        when(paymentProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(existing));

        service.deleteProfile(email);

        verify(paymentProfileRepository).delete(existing);
    }

    @Test
    @DisplayName("deleteProfile does nothing when profile does not exist")
    void testDeleteProfile_notExists() {
        mockFindUser();
        when(paymentProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.empty());

        service.deleteProfile(email);

        verify(paymentProfileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when IFSC is blank")
    void testSaveOrUpdate_blankIfsc() {
        mockFindUser();
        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "HDFC Bank", "123456789012", "   ", "John Doe");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(req, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("saveOrUpdateProfile throws BAD_REQUEST when account number is blank")
    void testSaveOrUpdate_blankAccount() {
        mockFindUser();
        UserPaymentProfileRequestDto req = new UserPaymentProfileRequestDto(
                "HDFC Bank", "   ", "HDFC0001234", "John Doe");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveOrUpdateProfile(req, email));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("findByUserUuid returns entity or null")
    void testFindByUserUuid() {
        assertNull(service.findByUserUuid(null));
        assertNull(service.findByUserUuid(""));
        assertNull(service.findByUserUuid("   "));

        when(paymentProfileRepository.findByUserUuid("u-nonexistent")).thenReturn(Optional.empty());
        assertNull(service.findByUserUuid("u-nonexistent"));

        UserPaymentProfile profile = UserPaymentProfile.builder().uuid("prof-1").build();
        when(paymentProfileRepository.findByUserUuid("u-1")).thenReturn(Optional.of(profile));

        assertEquals(profile, service.findByUserUuid("u-1"));
    }

    @Test
    @DisplayName("findUserByIdentifier resolves user via UserProvider")
    void testFindUserByIdentifier_viaProvider() {
        UserProvider provider = new UserProvider();
        provider.setUserUuid(userUuid);
        provider.setProviderId("auth0|123");

        when(userProviderRepository.findByProviderId("auth0|123")).thenReturn(Optional.of(provider));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(mockUser));
        when(paymentProfileRepository.findByUserUuid(userUuid)).thenReturn(Optional.empty());

        UserPaymentProfileResponseDto result = service.getProfile("auth0|123");
        assertNull(result);
    }
}
