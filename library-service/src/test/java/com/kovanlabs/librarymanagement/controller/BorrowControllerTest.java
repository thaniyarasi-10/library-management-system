package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.service.BorrowService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BorrowControllerTest {

    @Mock
    private BorrowService borrowService;

    @Mock
    private Principal principal;

    @InjectMocks
    private BorrowController borrowController;

    private String borrowUuid;

    @BeforeEach
    void setUp() {
        borrowUuid = UUID.randomUUID().toString();
    }

    @Test
    @DisplayName("getAllBorrows should return list of borrows")
    void getAllBorrows_shouldReturnList() {
        BorrowResponseDto dto = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(borrowUuid)
                .status(BorrowStatus.BORROWED)
                .build();
        when(borrowService.getAllBorrows()).thenReturn(List.of(dto));

        List<BorrowResponseDto> result = borrowController.getAllBorrows();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(borrowUuid, result.get(0).borrowUuid());
        verify(borrowService).getAllBorrows();
    }

    @Test
    @DisplayName("getBorrowsByUserId should return borrows for user")
    void getBorrowsByUserId_shouldReturnList() {
        BorrowResponseDto dto = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(borrowUuid)
                .status(BorrowStatus.BORROWED)
                .build();
        when(borrowService.getBorrowsByUserId(10L)).thenReturn(List.of(dto));

        List<BorrowResponseDto> result = borrowController.getBorrowsByUserId(10L);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(borrowService).getBorrowsByUserId(10L);
    }

    @Test
    @DisplayName("getMyBorrows with principal should return user's borrows")
    void getMyBorrows_withPrincipal_shouldReturnList() {
        when(principal.getName()).thenReturn("test@example.com");
        BorrowResponseDto dto = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(borrowUuid)
                .status(BorrowStatus.BORROWED)
                .build();
        when(borrowService.getBorrowsByUserEmail("test@example.com")).thenReturn(List.of(dto));

        List<BorrowResponseDto> result = borrowController.getMyBorrows(principal);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(borrowService).getBorrowsByUserEmail("test@example.com");
    }

    @Test
    @DisplayName("getMyBorrows with null principal should return empty list")
    void getMyBorrows_withNullPrincipal_shouldReturnEmptyList() {
        List<BorrowResponseDto> result = borrowController.getMyBorrows(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(borrowService);
    }

    @Test
    @DisplayName("borrowBook with principal should call service with identifier and return 201 Created")
    void borrowBook_withPrincipal_shouldReturnCreated() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);
        BorrowResponseDto response = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(borrowUuid)
                .status(BorrowStatus.BORROWED)
                .build();
        when(principal.getName()).thenReturn("test@example.com");
        when(borrowService.borrowBook(eq(request), eq("test@example.com"))).thenReturn(response);

        ResponseEntity<BorrowResponseDto> result = borrowController.borrowBook(request, principal);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1L, result.getBody().id());
        verify(borrowService).borrowBook(request, "test@example.com");
    }

    @Test
    @DisplayName("borrowBook with null principal should call service with null identifier and return 201 Created")
    void borrowBook_withNullPrincipal_shouldReturnCreated() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);
        BorrowResponseDto response = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(borrowUuid)
                .status(BorrowStatus.BORROWED)
                .build();
        when(borrowService.borrowBook(eq(request), isNull())).thenReturn(response);

        ResponseEntity<BorrowResponseDto> result = borrowController.borrowBook(request, null);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1L, result.getBody().id());
        verify(borrowService).borrowBook(request, null);
    }

    @Test
    @DisplayName("returnBook should return updated borrow response")
    void returnBook_shouldReturnUpdatedBorrowResponse() {
        BorrowResponseDto response = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(borrowUuid)
                .status(BorrowStatus.RETURNED)
                .build();
        when(borrowService.returnBook(1L)).thenReturn(response);

        BorrowResponseDto result = borrowController.returnBook(1L);

        assertNotNull(result);
        assertEquals(BorrowStatus.RETURNED, result.status());
        verify(borrowService).returnBook(1L);
    }
}
