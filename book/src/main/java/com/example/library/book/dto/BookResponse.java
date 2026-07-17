package com.example.library.book.dto;

public record BookResponse(
    Long id,
    String title,
    String author,
    String isbn
) {}
