package com.kovanlabs.librarymanagement.notification.dto;

import java.time.LocalDate;

public record OverdueBookDto(
        String title,
        String author,
        LocalDate dueDate,
        long daysOverdue,
        double fine
) {
}
