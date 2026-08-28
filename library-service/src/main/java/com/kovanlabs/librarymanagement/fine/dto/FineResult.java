package com.kovanlabs.librarymanagement.fine.dto;

import com.kovanlabs.librarymanagement.database.entity.Borrow;

public record FineResult(
        Borrow borrow,
        long daysOverdue,
        double fine
) {
}

