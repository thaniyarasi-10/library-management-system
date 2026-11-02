package com.kovanlabs.librarymanagement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.dto.BookAvailabilityStatus;
import com.kovanlabs.librarymanagement.dto.BookRecommendationRequest;
import com.kovanlabs.librarymanagement.dto.BookRecommendationResponse;
import com.kovanlabs.librarymanagement.dto.RecommendationIntent;
import com.kovanlabs.librarymanagement.dto.RecommendedBookItem;
import com.kovanlabs.librarymanagement.exception.LibraryGlobalExceptionHandler;
import com.kovanlabs.librarymanagement.service.BookRecommendationService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookRecommendationControllerTest {

    @Mock
    private BookRecommendationService bookRecommendationService;

    @InjectMocks
    private BookRecommendationController bookRecommendationController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookRecommendationController)
                .setControllerAdvice(new LibraryGlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/ai/book-recommendations with valid request returns 200 OK and recommendations")
    void getRecommendations_ValidRequest_Returns200AndRecommendations() throws Exception {
        RecommendedBookItem item = new RecommendedBookItem(
                "The Intelligent Investor",
                "Benjamin Graham",
                "Classic book on value investing.",
                "9780060555665",
                BookAvailabilityStatus.AVAILABLE,
                3,
                5,
                2,
                "uuid-123",
                1L,
                "https://example.com/cover.jpg",
                BookCategory.NON_FICTION
        );

        BookRecommendationResponse response = BookRecommendationResponse.success(
                "Recommended books based on your interest: \"finance\"",
                List.of(item)
        );

        when(bookRecommendationService.getRecommendations(any(BookRecommendationRequest.class)))
                .thenReturn(response);

        BookRecommendationRequest request = new BookRecommendationRequest("I want to learn finance and investing");

        mockMvc.perform(post("/api/ai/book-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("BOOK_RECOMMENDATION"))
                .andExpect(jsonPath("$.recommendations[0].title").value("The Intelligent Investor"))
                .andExpect(jsonPath("$.recommendations[0].availabilityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.recommendations[0].availableCopies").value(3))
                .andExpect(jsonPath("$.recommendations[0].totalCopies").value(5))
                .andExpect(jsonPath("$.recommendations[0].borrowedCopies").value(2));

        verify(bookRecommendationService).getRecommendations(any(BookRecommendationRequest.class));
    }

    @Test
    @DisplayName("POST /api/ai/book-recommendations with out-of-scope query returns 200 OK with OUT_OF_SCOPE intent")
    void getRecommendations_OutOfScopeQuery_Returns200WithOutOfScope() throws Exception {
        BookRecommendationResponse outOfScopeResponse = BookRecommendationResponse.outOfScope();

        when(bookRecommendationService.getRecommendations(any(BookRecommendationRequest.class)))
                .thenReturn(outOfScopeResponse);

        BookRecommendationRequest request = new BookRecommendationRequest("Write python code for bubble sort");

        mockMvc.perform(post("/api/ai/book-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("OUT_OF_SCOPE"))
                .andExpect(jsonPath("$.message").value(BookRecommendationResponse.OUT_OF_SCOPE_MESSAGE))
                .andExpect(jsonPath("$.recommendations").isEmpty());

        verify(bookRecommendationService).getRecommendations(any(BookRecommendationRequest.class));
    }

    @Test
    @DisplayName("POST /api/ai/book-recommendations with blank query returns 400 Bad Request")
    void getRecommendations_BlankQuery_Returns400BadRequest() throws Exception {
        BookRecommendationRequest request = new BookRecommendationRequest("   ");

        mockMvc.perform(post("/api/ai/book-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookRecommendationService);
    }

    @Test
    @DisplayName("POST /api/ai/book-recommendations when AI service fails returns 503 Service Unavailable")
    void getRecommendations_AiServiceFailure_Returns503() throws Exception {
        when(bookRecommendationService.getRecommendations(any(BookRecommendationRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI recommendation service is temporarily unavailable."));

        BookRecommendationRequest request = new BookRecommendationRequest("Recommend sci-fi books");

        mockMvc.perform(post("/api/ai/book-recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());

        verify(bookRecommendationService).getRecommendations(any(BookRecommendationRequest.class));
    }
}

