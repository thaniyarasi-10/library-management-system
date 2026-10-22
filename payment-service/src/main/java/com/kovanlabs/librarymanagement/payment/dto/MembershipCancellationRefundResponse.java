package com.kovanlabs.librarymanagement.payment.dto;

public record MembershipCancellationRefundResponse(
        boolean cancelled,
        Long originalDepositAmount,
        Long unpaidFinesDeducted,
        Long refundAmount,
        String razorpayRefundId,
        String message
) {
}
