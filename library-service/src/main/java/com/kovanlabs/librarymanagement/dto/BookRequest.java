package com.kovanlabs.librarymanagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BookRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Author is required") String author,
        @NotBlank(message = "ISBN is required") String isbn,
        @Min(value = 0, message = "Book count must be non-negative") Integer bookCount
) {
    public BookRequest(String title, String author, String isbn) {
        this(title, author, isbn, 0);
    }
}
