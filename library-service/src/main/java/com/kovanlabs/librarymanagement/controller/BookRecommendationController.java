package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.dto.BookRecommendationRequest;
import com.kovanlabs.librarymanagement.dto.BookRecommendationResponse;
import com.kovanlabs.librarymanagement.service.BookRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing endpoints for AI-powered book recommendations.
 */
@RestController
@RequestMapping({"/api/ai/book-recommendations", "/ai/book-recommendations"})
@RequiredArgsConstructor
public class BookRecommendationController {

    private final BookRecommendationService bookRecommendationService;

    /**
     * Endpoint to request AI book recommendations for a given natural language interest.
     * Rejects out-of-scope queries and matches recommendations with MySQL catalog availability.
     *
     * @param request Validated request payload containing user query
     * @return 200 OK with {@link BookRecommendationResponse}
     */
    @PostMapping
    public ResponseEntity<BookRecommendationResponse> getRecommendations(
            @Valid @RequestBody BookRecommendationRequest request) {
        BookRecommendationResponse response = bookRecommendationService.getRecommendations(request);
        return ResponseEntity.ok(response);
    }
}

