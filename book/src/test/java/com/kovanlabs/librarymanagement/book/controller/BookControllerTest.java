package com.kovanlabs.librarymanagement.book.controller;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookController).build();
    }

    @Test
    @DisplayName("GET /books/{id} when book exists should return 200 OK and BookResponse")
    void getBookById_WhenBookExists_ShouldReturn200OkAndBook() throws Exception {
        BookResponse response = new BookResponse(1L, "Clean Code", "Robert C. Martin", "9780132350884");

        when(bookService.getBookById(1L)).thenReturn(response);

        mockMvc.perform(get("/books/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.author").value("Robert C. Martin"))
                .andExpect(jsonPath("$.isbn").value("9780132350884"));

        verify(bookService, times(1)).getBookById(1L);
    }

    @Test
    @DisplayName("GET /books/{id} when book not found should return 404 NOT_FOUND")
    void getBookById_WhenBookNotFound_ShouldReturn404() throws Exception {
        when(bookService.getBookById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: 99"));

        mockMvc.perform(get("/books/99")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(bookService, times(1)).getBookById(99L);
    }

    @Test
    @DisplayName("GET /books with default params should return 200 OK and PagedResponse")
    void getAllBooks_WithDefaultParams_ShouldReturnPagedBooks() throws Exception {
        BookResponse b1 = new BookResponse(1L, "Clean Code", "Robert C. Martin", "9780132350884");
        BookResponse b2 = new BookResponse(2L, "Effective Java", "Joshua Bloch", "9780134685991");
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
    @DisplayName("PUT /books/{id} when book exists should return 200 OK and updated BookResponse")
    void updateBook_WhenBookExists_ShouldReturn200OkAndUpdatedBook() throws Exception {
        BookResponse response = new BookResponse(1L, "Clean Architecture", "Robert C. Martin", "9780134494166");

        when(bookService.updateBook(eq(1L), any(BookRequest.class))).thenReturn(response);

        String jsonPayload = """
                {
                    "title": "Clean Architecture",
                    "author": "Robert C. Martin",
                    "isbn": "9780134494166"
                }
                """;

        mockMvc.perform(put("/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Clean Architecture"))
                .andExpect(jsonPath("$.author").value("Robert C. Martin"))
                .andExpect(jsonPath("$.isbn").value("9780134494166"));

        verify(bookService, times(1)).updateBook(eq(1L), any(BookRequest.class));
    }

    @Test
    @DisplayName("PUT /books/{id} when book not found should return 404 NOT_FOUND")
    void updateBook_WhenBookNotFound_ShouldReturn404() throws Exception {
        when(bookService.updateBook(eq(99L), any(BookRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: 99"));

        String jsonPayload = """
                {
                    "title": "Clean Architecture",
                    "author": "Robert C. Martin",
                    "isbn": "9780134494166"
                }
                """;

        mockMvc.perform(put("/books/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound());

        verify(bookService, times(1)).updateBook(eq(99L), any(BookRequest.class));
    }

    @Test
    @DisplayName("DELETE /books/{id} when book exists should return 204 No Content")
    void deleteBook_WhenBookExists_ShouldReturn204NoContent() throws Exception {
        doNothing().when(bookService).deleteBook(1L);

        mockMvc.perform(delete("/books/1"))
                .andExpect(status().isNoContent());

        verify(bookService, times(1)).deleteBook(1L);
    }

    @Test
    @DisplayName("DELETE /books/{id} when book not found should return 404 NOT_FOUND")
    void deleteBook_WhenBookNotFound_ShouldReturn404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found with ID: 99"))
                .when(bookService).deleteBook(99L);

        mockMvc.perform(delete("/books/99"))
                .andExpect(status().isNotFound());

        verify(bookService, times(1)).deleteBook(99L);
    }
}
