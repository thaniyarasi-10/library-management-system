package com.kovanlabs.librarymanagement.payment.controller;

import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileRequestDto;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileResponseDto;
import com.kovanlabs.librarymanagement.payment.service.UserPaymentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPaymentProfileControllerTest {

    @Mock
    private UserPaymentProfileService paymentProfileService;

    @Mock
    private Principal principal;

    @InjectMocks
    private UserPaymentProfileController controller;

    private final String email = "john.doe@example.com";
    private UserPaymentProfileResponseDto mockResponseDto;
    private UserPaymentProfileRequestDto mockRequestDto;

    @BeforeEach
    void setUp() {
        mockResponseDto = new UserPaymentProfileResponseDto(
                "profile-uuid-123",
                "user-uuid-456",
                "John Doe",
                "State Bank of India",
                "••••••••1234",
                "SBIN0001234",
                LocalDateTime.now()
        );

        mockRequestDto = new UserPaymentProfileRequestDto(
                "State Bank of India",
                "123456789012",
                "SBIN0001234",
                "John Doe"
        );
    }

    @Test
    @DisplayName("getPaymentProfile returns profile for valid principal")
    void testGetPaymentProfile_validPrincipal() {
        when(principal.getName()).thenReturn(email);
        when(paymentProfileService.getProfile(email)).thenReturn(mockResponseDto);

        ResponseEntity<UserPaymentProfileResponseDto> response = controller.getPaymentProfile(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("John Doe", response.getBody().accountHolderName());
        assertEquals("SBIN0001234", response.getBody().ifscCode());
        verify(paymentProfileService).getProfile(email);
    }

    @Test
    @DisplayName("getPaymentProfile returns 401 when principal is null")
    void testGetPaymentProfile_nullPrincipal() {
        ResponseEntity<UserPaymentProfileResponseDto> response = controller.getPaymentProfile(null);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(paymentProfileService);
    }

    @Test
    @DisplayName("getPaymentProfile returns 401 when principal name is null")
    void testGetPaymentProfile_nullPrincipalName() {
        when(principal.getName()).thenReturn(null);

        ResponseEntity<UserPaymentProfileResponseDto> response = controller.getPaymentProfile(principal);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(paymentProfileService);
    }

    @Test
    @DisplayName("saveOrUpdatePaymentProfile returns updated profile for valid input")
    void testSaveOrUpdatePaymentProfile_validInput() {
        when(principal.getName()).thenReturn(email);
        when(paymentProfileService.saveOrUpdateProfile(mockRequestDto, email)).thenReturn(mockResponseDto);

        ResponseEntity<UserPaymentProfileResponseDto> response =
                controller.saveOrUpdatePaymentProfile(mockRequestDto, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("State Bank of India", response.getBody().bankName());
        verify(paymentProfileService).saveOrUpdateProfile(mockRequestDto, email);
    }

    @Test
    @DisplayName("saveOrUpdatePaymentProfile returns 401 when principal is null")
    void testSaveOrUpdatePaymentProfile_nullPrincipal() {
        ResponseEntity<UserPaymentProfileResponseDto> response =
                controller.saveOrUpdatePaymentProfile(mockRequestDto, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(paymentProfileService);
    }

    @Test
    @DisplayName("saveOrUpdatePaymentProfile returns 401 when principal name is null")
    void testSaveOrUpdatePaymentProfile_nullPrincipalName() {
        when(principal.getName()).thenReturn(null);

        ResponseEntity<UserPaymentProfileResponseDto> response =
                controller.saveOrUpdatePaymentProfile(mockRequestDto, principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(paymentProfileService);
    }

    @Test
    @DisplayName("deletePaymentProfile returns 204 NoContent for valid principal")
    void testDeletePaymentProfile_validPrincipal() {
        when(principal.getName()).thenReturn(email);
        doNothing().when(paymentProfileService).deleteProfile(email);

        ResponseEntity<Void> response = controller.deletePaymentProfile(principal);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(paymentProfileService).deleteProfile(email);
    }

    @Test
    @DisplayName("deletePaymentProfile returns 401 when principal is null")
    void testDeletePaymentProfile_nullPrincipal() {
        ResponseEntity<Void> response = controller.deletePaymentProfile(null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(paymentProfileService);
    }

    @Test
    @DisplayName("deletePaymentProfile returns 401 when principal name is null")
    void testDeletePaymentProfile_nullPrincipalName() {
        when(principal.getName()).thenReturn(null);

        ResponseEntity<Void> response = controller.deletePaymentProfile(principal);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(paymentProfileService);
    }
}
