package com.kovanlabs.librarymanagement.payment.service;

import com.kovanlabs.librarymanagement.payment.dto.MembershipCancellationRefundResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentOrderResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.VerifyPaymentRequest;

import java.util.List;

public interface PaymentService {

    PaymentOrderResponse createMembershipOrder(String email);

    PaymentOrderResponse createFineOrder(Long fineId, String email);

    PaymentResponseDto verifyPayment(VerifyPaymentRequest request, String email);

    void handleWebhook(String payload, String signatureHeader);

    MembershipCancellationRefundResponse cancelMembershipWithRefund(String email);

    PaymentResponseDto processPermanentDeactivationRefund(String userUuid);

    List<PaymentResponseDto> getUserPayments(String email);
}
