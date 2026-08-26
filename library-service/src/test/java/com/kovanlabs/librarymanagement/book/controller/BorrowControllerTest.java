package com.kovanlabs.librarymanagement.book.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.book.service.BorrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BorrowControllerTest {

    @Mock
    private BorrowService borrowService;

    @InjectMocks
    private BorrowController borrowController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(borrowController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void borrowBook_shouldReturnCreated() throws Exception {
        Long bookId = 10L;
        Long userId = 1L;
        BorrowRequestDto request = new BorrowRequestDto(bookId, userId);
        BorrowResponseDto response = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(UUID.randomUUID())
                .status(com.kovanlabs.librarymanagement.database.enums.BorrowStatus.BORROWED)
                .build();

        when(borrowService.borrowBook(any(BorrowRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("BORROWED"));
    }

    @Test
    void returnBook_shouldReturnUpdatedBorrowResponse() throws Exception {
        BorrowResponseDto response = BorrowResponseDto.builder()
                .id(1L)
                .borrowUuid(UUID.randomUUID())
                .status(com.kovanlabs.librarymanagement.database.enums.BorrowStatus.RETURNED)
                .build();

        when(borrowService.returnBook(1L)).thenReturn(response);

        mockMvc.perform(patch("/borrow/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RETURNED"));
    }
}
