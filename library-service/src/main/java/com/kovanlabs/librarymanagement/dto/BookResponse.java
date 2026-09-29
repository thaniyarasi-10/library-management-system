package com.kovanlabs.librarymanagement.dto;

import java.io.Serializable;

public record BookResponse(
        String uuid,
        Long id,
        String title,
        String author,
        String isbn,
        String coverImageUrl
) implements Serializable {
}

