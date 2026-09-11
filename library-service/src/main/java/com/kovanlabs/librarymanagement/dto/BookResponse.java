package com.kovanlabs.librarymanagement.dto;

import java.io.Serializable;
import java.util.UUID;

public record BookResponse(
        UUID uuid,
        Long id,
        String title,
        String author,
        String isbn,
        String coverImageUrl
) implements Serializable {
}

