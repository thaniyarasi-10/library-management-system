package com.kovanlabs.librarymanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookDonationRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Author is required")
        String author,

        @NotBlank(message = "ISBN is required")
        String isbn,

        @NotNull(message = "Donated book count is required")
        @Min(value = 1, message = "Donated book count must be at least 1")
        Integer donatedBookCount
) {
}
