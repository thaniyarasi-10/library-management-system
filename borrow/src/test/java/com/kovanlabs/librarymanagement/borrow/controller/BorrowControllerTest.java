package com.kovanlabs.librarymanagement.borrow.controller;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.borrow.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.borrow.service.BorrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BorrowControllerTest {

    @Mock
    private BorrowService borrowService;

    @InjectMocks
    private BorrowController borrowController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(borrowController).build();
    }

    @Test
    @DisplayName("GET /borrow/search should return paged borrowed books")
    void searchBorrowedBooks_ShouldReturnPagedBorrowedBooks() throws Exception {
        BorrowResponseDto response = BorrowResponseDto.builder()
                .borrowId(1L)
                .userId(2L)
                .bookId(3L)
                .borrowDate(LocalDate.of(2026, 8, 1))
                .dueDate(LocalDate.of(2026, 8, 15))
                .returnedDate(null)
                .status(BorrowStatus.BORROWED)
                .build();

        PagedResponse<BorrowResponseDto> pagedResponse = new PagedResponse<>(
                List.of(response), 0, 10, 1L, 1, true
        );

        when(borrowService.searchBorrowedBooks("clean", 0, 10, "id", "asc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/borrow/search")
                        .param("query", "clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].borrowId").value(1))
                .andExpect(jsonPath("$.content[0].bookId").value(3))
                .andExpect(jsonPath("$.pageNo").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(borrowService).searchBorrowedBooks("clean", 0, 10, "id", "asc");
    }

    @Test
    @DisplayName("GET /borrow/search with invalid sortBy should return 400 Bad Request")
    void searchBorrowedBooks_WithInvalidSortBy_ShouldReturnBadRequest() throws Exception {
        when(borrowService.searchBorrowedBooks("clean", 0, 10, "invalidField", "asc"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sortBy field: invalidField"));

        mockMvc.perform(get("/borrow/search")
                        .param("query", "clean")
                        .param("sortBy", "invalidField"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /borrow/search with invalid sortDir should return 400 Bad Request")
    void searchBorrowedBooks_WithInvalidSortDir_ShouldReturnBadRequest() throws Exception {
        when(borrowService.searchBorrowedBooks("clean", 0, 10, "id", "invalidDir"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sortDir: invalidDir"));

        mockMvc.perform(get("/borrow/search")
                        .param("query", "clean")
                        .param("sortDir", "invalidDir"))
                .andExpect(status().isBadRequest());
    }
}