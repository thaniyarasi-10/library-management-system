package com.kovanlabs.librarymanagement.payment.dto;

import java.util.Map;

public record RazorpayOrderDetails(
        String id,
        Long amount,
        Map<String, String> notes
) {
}