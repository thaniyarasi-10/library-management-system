package com.kovanlabs.librarymanagement.book.dto;

import java.util.UUID;

public record BookResponse(
        UUID uuid,
        Long id,
        String title,
        String author,
        String isbn
) {
}
