package com.kovanlabs.librarymanagement.dto;

public record MembershipCancellationRefundResponse(
        boolean cancelled,
        Long originalDepositAmount,
        Long unpaidFinesDeducted,
        Long refundAmount,
        String razorpayRefundId,
        String message
) {
}
