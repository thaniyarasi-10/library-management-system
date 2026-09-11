package com.kovanlabs.librarymanagement.dto;

import com.kovanlabs.librarymanagement.database.entity.Borrow;

public record FineResult(
        Borrow borrow,
        long daysOverdue,
        double fine
) {
}

