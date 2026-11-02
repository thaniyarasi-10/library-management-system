package com.kovanlabs.librarymanagement.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Structured response for AI book recommendation queries.
 */
public record BookRecommendationResponse(
        RecommendationIntent intent,
        String message,
        List<RecommendedBookItem> recommendations
) implements Serializable {

    public static final String OUT_OF_SCOPE_MESSAGE =
            "I can only assist with book recommendations and reading suggestions. Please ask about books, authors, genres, or topics you want to read or learn about.";

    public static BookRecommendationResponse outOfScope() {
        return new BookRecommendationResponse(
                RecommendationIntent.OUT_OF_SCOPE,
                OUT_OF_SCOPE_MESSAGE,
                List.of()
        );
    }

    public static BookRecommendationResponse outOfScope(String customMessage) {
        return new BookRecommendationResponse(
                RecommendationIntent.OUT_OF_SCOPE,
                customMessage != null ? customMessage : OUT_OF_SCOPE_MESSAGE,
                List.of()
        );
    }

    public static BookRecommendationResponse success(String message, List<RecommendedBookItem> recommendations) {
        return new BookRecommendationResponse(
                RecommendationIntent.BOOK_RECOMMENDATION,
                message,
                recommendations != null ? recommendations : List.of()
        );
    }
}

