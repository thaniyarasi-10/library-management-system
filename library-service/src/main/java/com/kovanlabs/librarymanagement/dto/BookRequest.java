package com.kovanlabs.librarymanagement.dto;

import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Author is required") String author,
        @NotBlank(message = "ISBN is required") String isbn,
        @NotNull(message = "Total book count is required")
        @Min(value = 1, message = "Total book count must be at least 1")
        Integer totalBookCount,
        @NotNull(message = "Category is required") BookCategory category
) {}
