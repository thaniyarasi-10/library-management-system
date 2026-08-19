package com.kovanlabs.librarymanagement.fine.dto;

import com.kovanlabs.librarymanagement.borrow.entity.Borrow;

public record FineResult(
        Borrow borrow,
        long daysOverdue,
        double fine
) {
}
