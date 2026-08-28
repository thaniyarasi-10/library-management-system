package com.kovanlabs.librarymanagement.book.controller;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.book.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

    @Mock
    private BookService bookService;

    @InjectMocks
    private BookController bookController;

    private MockMvc mockMvc;
    private UUID uuid1;
    private UUID uuid2;
    private Long id1;
    private Long id2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookController).build();
        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        id1 = 1L;
        id2 = 2L;
    }

    @Test
    @DisplayName("GET /books/{id} when book exists should return 200 OK and BookResponse")
    void getBookById_WhenBookExists_ShouldReturn200OkAndBook() throws Exception {
        BookResponse response = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884");

        when(bookService.getBookById(id1)).thenReturn(response);

        mockMvc.perform(get("/books/" + id1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.author").value("Robert C. Martin"))
                .andExpect(jsonPath("$.isbn").value("9780132350884"));

        verify(bookService, times(1)).getBookById(id1);
    }

    @Test
    @DisplayName("GET /books with default params should return 200 OK and PagedResponse")
    void getAllBooks_WithDefaultParams_ShouldReturnPagedBooks() throws Exception {
        BookResponse b1 = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884");
        BookResponse b2 = new BookResponse(uuid2, id2, "Effective Java", "Joshua Bloch", "9780134685991");
        PagedResponse<BookResponse> pagedResponse = new PagedResponse<>(
                List.of(b1, b2), 0, 10, 2L, 1, true
        );

        when(bookService.getAllBooks(0, 10, "id", "asc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/books")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Clean Code"))
                .andExpect(jsonPath("$.content[1].title").value("Effective Java"))
                .andExpect(jsonPath("$.pageNo").value(0))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));

        verify(bookService, times(1)).getAllBooks(0, 10, "id", "asc");
    }

    @Test
    @DisplayName("DELETE /books/{id} when book exists should return 204 No Content")
    void deleteBook_WhenBookExists_ShouldReturn204NoContent() throws Exception {
        doNothing().when(bookService).deleteBook(id1);

        mockMvc.perform(delete("/books/" + id1))
                .andExpect(status().isNoContent());

        verify(bookService, times(1)).deleteBook(id1);
    }
}
