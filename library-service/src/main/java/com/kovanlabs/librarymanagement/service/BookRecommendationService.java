package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BookRecommendationRequest;
import com.kovanlabs.librarymanagement.dto.BookRecommendationResponse;

/**
 * Service interface for AI-powered book recommendations with intent restrictions
 * and real-time MySQL database availability verification.
 */
public interface BookRecommendationService {

    /**
     * Processes a user request, validates scope/intent, queries Gemini via ChatClient,
     * checks catalog matching and calculates copy availability from MySQL.
     *
     * @param request The user recommendation query payload
     * @return {@link BookRecommendationResponse} containing structured recommendations or OUT_OF_SCOPE response
     */
    BookRecommendationResponse getRecommendations(BookRecommendationRequest request);
}

