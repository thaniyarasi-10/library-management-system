package com.kovanlabs.librarymanagement.communication.dto;

import java.time.LocalDate;

public record OverdueBookDto(
        String title,
        String author,
        LocalDate dueDate,
        long daysOverdue,
        double fine
) {
}

