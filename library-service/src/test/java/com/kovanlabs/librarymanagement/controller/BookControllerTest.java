package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.database.dto.PagedResponse;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

    @Mock
    private BookService bookService;

    @InjectMocks
    private BookController bookController;

    private MockMvc mockMvc;
    private String uuid1;
    private String uuid2;
    private Long id1;
    private Long id2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookController).build();
        uuid1 = UUID.randomUUID().toString();
        uuid2 = UUID.randomUUID().toString();
        id1 = 1L;
        id2 = 2L;
    }

    @Test
    @DisplayName("GET /api/books/{uuid} when book exists should return 200 OK and BookResponse")
    void getBookById_WhenBookExists_ShouldReturn200OkAndBook() throws Exception {
        BookResponse response = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884", null, 5, 0, 5, BookCategory.TECHNOLOGY);

        when(bookService.getBookByUuid(uuid1)).thenReturn(response);

        mockMvc.perform(get("/api/books/" + uuid1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.author").value("Robert C. Martin"))
                .andExpect(jsonPath("$.isbn").value("9780132350884"))
                .andExpect(jsonPath("$.category").value("TECHNOLOGY"));

        verify(bookService, times(1)).getBookByUuid(uuid1);
    }

    @Test
    @DisplayName("GET /api/books with default params should return 200 OK and PagedResponse")
    void getAllBooks_WithDefaultParams_ShouldReturnPagedBooks() throws Exception {
        BookResponse b1 = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884", null, 5, 0, 5, BookCategory.TECHNOLOGY);
        BookResponse b2 = new BookResponse(uuid2, id2, "Effective Java", "Joshua Bloch", "9780134685991", null, 3, 0, 3, BookCategory.TECHNOLOGY);
        PagedResponse<BookResponse> pagedResponse = new PagedResponse<>(
                List.of(b1, b2), 0, 10, 2L, 1, true
        );

        when(bookService.getAllBooks(0, 10, "id", "asc", null)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/books")
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

        verify(bookService, times(1)).getAllBooks(0, 10, "id", "asc", null);
    }

    @Test
    @DisplayName("GET /api/books with category filter should return filtered books")
    void getAllBooks_WithCategoryParam_ShouldReturnFilteredBooks() throws Exception {
        BookResponse b1 = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884", null, 5, 0, 5, BookCategory.TECHNOLOGY);
        PagedResponse<BookResponse> pagedResponse = new PagedResponse<>(
                List.of(b1), 0, 10, 1L, 1, true
        );

        when(bookService.getAllBooks(0, 10, "id", "asc", BookCategory.TECHNOLOGY)).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/books").param("category", "TECHNOLOGY")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Clean Code"));

        verify(bookService, times(1)).getAllBooks(0, 10, "id", "asc", BookCategory.TECHNOLOGY);
    }

    @Test
    @DisplayName("DELETE /api/books/{uuid} when book exists should return 204 No Content")
    void deleteBook_WhenBookExists_ShouldReturn204NoContent() throws Exception {
        doNothing().when(bookService).deleteBook(uuid1);

        mockMvc.perform(delete("/api/books/" + uuid1))
                .andExpect(status().isNoContent());

        verify(bookService, times(1)).deleteBook(uuid1);
    }

    @Test
    @DisplayName("POST /api/books should create book")
    void createBook_ShouldReturnCreated() throws Exception {
        BookResponse response = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884", null, 5, 0, 5, BookCategory.TECHNOLOGY);
        when(bookService.createBook(any(BookRequest.class))).thenReturn(response);

        String body = "{\"title\":\"Clean Code\",\"author\":\"Robert C. Martin\",\"isbn\":\"9780132350884\",\"totalBookCount\":5,\"category\":\"TECHNOLOGY\"}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Clean Code"))
                .andExpect(jsonPath("$.category").value("TECHNOLOGY"));
    }

    @Test
    @DisplayName("GET /api/books/search should return search results")
    void searchBooks_ShouldReturnPagedBooks() throws Exception {
        BookResponse response = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884", null, 5, 0, 5, BookCategory.TECHNOLOGY);
        PagedResponse<BookResponse> pagedResponse = new PagedResponse<>(List.of(response), 0, 10, 1L, 1, true);

        when(bookService.searchBooks("Clean", null, 0, 10, "id", "asc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/books/search").param("query", "Clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Clean Code"));
    }

    @Test
    @DisplayName("GET /api/books/search with category should return filtered search results")
    void searchBooks_WithCategory_ShouldReturnPagedBooks() throws Exception {
        BookResponse response = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884", null, 5, 0, 5, BookCategory.TECHNOLOGY);
        PagedResponse<BookResponse> pagedResponse = new PagedResponse<>(List.of(response), 0, 10, 1L, 1, true);

        when(bookService.searchBooks("Clean", BookCategory.TECHNOLOGY, 0, 10, "id", "asc")).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/books/search").param("query", "Clean").param("category", "TECHNOLOGY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Clean Code"));
    }

    @Test
    @DisplayName("GET /api/books/categories should return all categories")
    void getCategories_ShouldReturnAllCategories() throws Exception {
        when(bookService.getAllCategories()).thenReturn(List.of(BookCategory.values()));

        mockMvc.perform(get("/api/books/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(BookCategory.values().length));

        verify(bookService, times(1)).getAllCategories();
    }

    @Test
    @DisplayName("PUT /api/books/{uuid} should update book")
    void updateBook_ShouldReturnUpdatedBook() throws Exception {
        BookResponse response = new BookResponse(uuid1, id1, "Clean Code", "Robert C. Martin", "9780132350884", null, 5, 0, 5, BookCategory.TECHNOLOGY);
        when(bookService.updateBook(eq(uuid1), any(BookRequest.class))).thenReturn(response);

        String body = "{\"title\":\"Clean Code\",\"author\":\"Robert C. Martin\",\"isbn\":\"9780132350884\",\"totalBookCount\":5,\"category\":\"TECHNOLOGY\"}";

        mockMvc.perform(put("/api/books/" + uuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code"));
    }

    @Test
    @DisplayName("POST /api/books/{uuid}/cover should upload cover image")
    void uploadCover_ShouldReturnMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", "content".getBytes());
        when(bookService.uploadBookCover(uuid1, file)).thenReturn("Book cover updated");

        mockMvc.perform(multipart("/api/books/" + uuid1 + "/cover").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("Book cover updated"));
    }

    @Test
    @DisplayName("GET /api/books/{uuid}/cover should return image url")
    void getBookCover_ShouldReturnUrl() throws Exception {
        when(bookService.getImageCoverByUuid(uuid1)).thenReturn("http://s3.com/cover.jpg");

        mockMvc.perform(get("/api/books/" + uuid1 + "/cover"))
                .andExpect(status().isOk())
                .andExpect(content().string("http://s3.com/cover.jpg"));
    }
}
