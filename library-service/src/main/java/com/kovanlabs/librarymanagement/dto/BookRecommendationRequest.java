package com.kovanlabs.librarymanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for AI book recommendations.
 *
 * @param query Natural language user interests, topic, or reading question.
 */
public record BookRecommendationRequest(
        @NotBlank(message = "Query must not be blank")
        @Size(max = 500, message = "Query must not exceed 500 characters")
        String query
) {
}

