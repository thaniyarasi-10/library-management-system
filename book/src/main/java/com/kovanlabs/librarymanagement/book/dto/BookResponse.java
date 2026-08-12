package com.kovanlabs.librarymanagement.book.dto;

public record BookResponse(
    Long id,
    String title,
    String author,
    String isbn
) {}
