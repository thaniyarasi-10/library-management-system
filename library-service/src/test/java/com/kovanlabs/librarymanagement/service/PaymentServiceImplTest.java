package com.kovanlabs.librarymanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.config.RazorpayProperties;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.*;
import com.kovanlabs.librarymanagement.database.repository.*;
import com.kovanlabs.librarymanagement.dto.MembershipCancellationRefundResponse;
import com.kovanlabs.librarymanagement.dto.PaymentOrderResponse;
import com.kovanlabs.librarymanagement.dto.PaymentResponseDto;
import com.kovanlabs.librarymanagement.dto.VerifyPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private FineRepository fineRepository;

    @Mock
    private BorrowRepository borrowRepository;

    @Mock
    private MembershipService membershipService;

    @Mock
    private FineService fineService;

    @Mock
    private RazorpayClientService razorpayClientService;

    @Mock
    private RazorpayProperties razorpayProperties;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User user;
    private final String userEmail = "member@example.com";
    private final String userUuid = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        user = User.builder()
                .uuid(userUuid)
                .email(userEmail)
                .name("Test Member")
                .role(RoleEnum.USER)
                .build();

        lenient().when(userProviderRepository.findByProviderId(anyString())).thenReturn(Optional.empty());
        lenient().when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        lenient().when(razorpayProperties.getKeyId()).thenReturn("rzp_test_key_abc");
    }

    // ==========================================
    // 1. Membership Order Creation
    // ==========================================

    @Test
    void testCreateMembershipOrder_success() {
        when(membershipService.hasActiveMembership(userUuid)).thenReturn(false);

        Membership pendingSigned = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .signed(true)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(pendingSigned));

        when(razorpayClientService.createOrder(eq(50000L), anyString(), anyMap()))
                .thenReturn("order_mem_123");

        PaymentOrderResponse response = paymentService.createMembershipOrder(userEmail);

        assertNotNull(response);
        assertEquals("order_mem_123", response.orderId());
        assertEquals(50000L, response.amount());
        assertEquals("MEMBERSHIP", response.paymentType());

        verify(paymentRepository).save(argThat(p ->
                p.getUserUuid().equals(userUuid) &&
                p.getTransactionType() == TransactionType.PAYMENT &&
                p.getPaymentType() == PaymentType.MEMBERSHIP &&
                p.getAmount() == 50000L &&
                p.getStatus() == PaymentStatus.PENDING
        ));
    }

    @Test
    void testCreateMembershipOrder_alreadyActive_throwsBadRequest() {
        when(membershipService.hasActiveMembership(userUuid)).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> paymentService.createMembershipOrder(userEmail));
    }

    @Test
    void testCreateMembershipOrder_notSigned_throwsBadRequest() {
        when(membershipService.hasActiveMembership(userUuid)).thenReturn(false);

        Membership unsigned = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .signed(false)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(unsigned));

        assertThrows(ResponseStatusException.class, () -> paymentService.createMembershipOrder(userEmail));
    }

    // ==========================================
    // 2. Fine Order Creation
    // ==========================================

    @Test
    void testCreateFineOrder_success() {
        Long fineId = 101L;
        Fine fine = Fine.builder()
                .id(fineId)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.valueOf(25.00)) // ₹25 = 2500 paise
                .status(FineStatus.PENDING)
                .build();

        when(fineRepository.findById(fineId)).thenReturn(Optional.of(fine));
        when(razorpayClientService.createOrder(eq(2500L), anyString(), anyMap()))
                .thenReturn("order_fine_101");

        PaymentOrderResponse response = paymentService.createFineOrder(fineId, userEmail);

        assertNotNull(response);
        assertEquals("order_fine_101", response.orderId());
        assertEquals(2500L, response.amount());
        assertEquals("FINE", response.paymentType());

        verify(paymentRepository).save(argThat(p ->
                p.getPaymentType() == PaymentType.FINE &&
                p.getAmount() == 2500L &&
                p.getStatus() == PaymentStatus.PENDING
        ));
    }

    @Test
    void testCreateFineOrder_fineBelongsToDifferentUser_forbidden() {
        Long fineId = 102L;
        Fine fine = Fine.builder()
                .id(fineId)
                .userUuid("different-user-uuid")
                .pendingFineAmount(BigDecimal.valueOf(15.00))
                .status(FineStatus.PENDING)
                .build();

        when(fineRepository.findById(fineId)).thenReturn(Optional.of(fine));

        assertThrows(ResponseStatusException.class, () -> paymentService.createFineOrder(fineId, userEmail));
    }

    @Test
    void testCreateFineOrder_alreadyPaid_throwsBadRequest() {
        Long fineId = 103L;
        Fine fine = Fine.builder()
                .id(fineId)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.valueOf(10.00))
                .status(FineStatus.PAID)
                .build();

        when(fineRepository.findById(fineId)).thenReturn(Optional.of(fine));

        assertThrows(ResponseStatusException.class, () -> paymentService.createFineOrder(fineId, userEmail));
    }

    // ==========================================
    // 3. Payment Verification
    // ==========================================

    @Test
    void testVerifyPayment_membershipSuccess() {
        String orderId = "order_m_1";
        String payId = "pay_m_1";
        String sig = "valid_sig";
        VerifyPaymentRequest request = new VerifyPaymentRequest(orderId, payId, sig);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .razorpayOrderId(orderId)
                .amount(50000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(payment));
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertEquals(payId, response.razorpayPaymentId());
        verify(membershipService).activateMembership(userUuid);
    }

    @Test
    void testVerifyPayment_fineSuccess() {
        String orderId = "order_f_1";
        String payId = "pay_f_1";
        String sig = "valid_sig";
        VerifyPaymentRequest request = new VerifyPaymentRequest(orderId, payId, sig);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.FINE)
                .transactionType(TransactionType.PAYMENT)
                .razorpayOrderId(orderId)
                .referenceId("101")
                .amount(2500L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(payment));
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        verify(fineService).payFine(101L);
    }

    @Test
    void testVerifyPayment_invalidSignature_marksFailedAndThrows() {
        String orderId = "order_m_2";
        String payId = "pay_m_2";
        String sig = "invalid_sig";
        VerifyPaymentRequest request = new VerifyPaymentRequest(orderId, payId, sig);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .razorpayOrderId(orderId)
                .amount(50000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(payment));
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> paymentService.verifyPayment(request, userEmail));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(paymentRepository).save(payment);
        verify(membershipService, never()).activateMembership(any());
    }

    @Test
    void testVerifyPayment_alreadySuccess_idempotent() {
        String orderId = "order_m_done";
        VerifyPaymentRequest request = new VerifyPaymentRequest(orderId, "pay_done", "sig_done");

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .razorpayOrderId(orderId)
                .razorpayPaymentId("pay_done")
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(payment));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        verify(membershipService, never()).activateMembership(any());
    }

    // ==========================================
    // 4. Webhook Processing
    // ==========================================

    @Test
    void testHandleWebhook_invalidSignature_throwsBadRequest() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> paymentService.handleWebhook("{}", "bad_sig"));
    }

    @Test
    void testHandleWebhook_orderPaid() {
        String payload = "{\"event\":\"order.paid\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_web_1\",\"id\":\"pay_web_1\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_web_1")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_web_1")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("pay_web_1", payment.getRazorpayPaymentId());
        verify(paymentRepository).save(payment);
        verify(membershipService).activateMembership(userUuid);
    }

    @Test
    void testHandleWebhook_paymentFailed() {
        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_fail_1\",\"id\":\"pay_fail_1\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_fail_1")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_fail_1")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void testHandleWebhook_missingSignature_throwsBadRequest() {
        assertThrows(ResponseStatusException.class, () -> paymentService.handleWebhook("{}", null));
        assertThrows(ResponseStatusException.class, () -> paymentService.handleWebhook("{}", "   "));
    }

    @Test
    void testHandleWebhook_missingPayload_throwsBadRequest() {
        assertThrows(ResponseStatusException.class, () -> paymentService.handleWebhook(null, "sig"));
        assertThrows(ResponseStatusException.class, () -> paymentService.handleWebhook("   ", "sig"));
    }

    @Test
    void testHandleWebhook_orderPaid_idempotent_skipsIfAlreadySuccess() {
        String payload = "{\"event\":\"order.paid\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_already_success\",\"id\":\"pay_new\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_already_success")
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_already_success")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, "good_sig");

        // Should NOT activate membership or save payment again
        verify(membershipService, never()).activateMembership(anyString());
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    void testHandleWebhook_paymentFailed_idempotent_skipsIfAlreadyFailed() {
        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_already_failed\",\"id\":\"pay_dup\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_already_failed")
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_already_failed")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, "good_sig");

        verify(paymentRepository, never()).save(payment);
    }

    @Test
    void testHandleWebhook_paymentFailed_ignoresIfAlreadySuccess() {
        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_was_success\",\"id\":\"pay_late_fail\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_was_success")
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_was_success")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, "good_sig");

        // Status must remain SUCCESS and not be overwritten
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    void testHandleWebhook_refundProcessed_idempotent_skipsIfAlreadySuccess() {
        String payload = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_dup_success\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .razorpayRefundId("rfnd_dup_success")
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_dup_success")).thenReturn(Optional.of(refund));

        paymentService.handleWebhook(payload, "sig");

        verify(paymentRepository, never()).save(refund);
    }

    @Test
    void testHandleWebhook_refundFailed_updatesStatusAndReevaluatesOriginal() {
        String payload = "{\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_failed_1\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        String origPaymentUuid = UUID.randomUUID().toString();
        Payment origPayment = Payment.builder()
                .uuid(origPaymentUuid)
                .userUuid(userUuid)
                .amount(50000L)
                .status(PaymentStatus.PARTIALLY_REFUNDED)
                .build();

        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentUuid(origPaymentUuid)
                .razorpayRefundId("rfnd_failed_1")
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.PENDING)
                .amount(50000L)
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_failed_1")).thenReturn(Optional.of(refund));
        when(paymentRepository.findByUuid(origPaymentUuid)).thenReturn(Optional.of(origPayment));
        when(paymentRepository.findByPaymentUuidAndTransactionType(origPaymentUuid, TransactionType.REFUND))
                .thenReturn(List.of()); // No other successful refunds

        paymentService.handleWebhook(payload, "sig");

        assertEquals(PaymentStatus.FAILED, refund.getStatus());
        assertEquals(PaymentStatus.SUCCESS, origPayment.getStatus());
        verify(paymentRepository).save(refund);
        verify(paymentRepository).save(origPayment);
    }

    @Test
    void testHandleWebhook_refundFailed_idempotent_skipsIfAlreadyFailed() {
        String payload = "{\"event\":\"refund.failed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_already_failed\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .razorpayRefundId("rfnd_already_failed")
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_already_failed")).thenReturn(Optional.of(refund));

        paymentService.handleWebhook(payload, "sig");

        verify(paymentRepository, never()).save(refund);
    }

    @Test
    void testHandleWebhook_unhandledEvent_safelyIgnored() {
        String payload = "{\"event\":\"payment.authorized\",\"payload\":{}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verifyNoInteractions(paymentRepository);
    }

    // ==========================================
    // 5. Membership Cancellation Refund
    // ==========================================

    @Test
    void testCancelMembershipWithRefund_partialDeductionForUnpaidFines() {
        Membership activeMem = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(activeMem));

        String origPaymentUuid = UUID.randomUUID().toString();
        Payment origPayment = Payment.builder()
                .uuid(origPaymentUuid)
                .userUuid(userUuid)
                .razorpayPaymentId("pay_orig_123")
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                eq(userUuid), eq(PaymentType.MEMBERSHIP), eq(TransactionType.PAYMENT), eq(PaymentStatus.SUCCESS)))
                .thenReturn(Optional.of(origPayment));

        // ₹50 unpaid fines (5000 paise) -> eligible refund = 50000 - 5000 = 45000 paise (₹450)
        when(fineService.calculateTotalPendingFineForUser(userUuid)).thenReturn(BigDecimal.valueOf(50.00));
        when(paymentRepository.findByPaymentUuidAndTransactionType(origPaymentUuid, TransactionType.REFUND))
                .thenReturn(List.of());
        when(razorpayClientService.refundPayment("pay_orig_123", 45000L)).thenReturn("rfnd_cancel_450");

        MembershipCancellationRefundResponse response = paymentService.cancelMembershipWithRefund(userEmail);

        assertTrue(response.cancelled());
        assertEquals(50000L, response.originalDepositAmount());
        assertEquals(5000L, response.unpaidFinesDeducted());
        assertEquals(45000L, response.refundAmount());
        assertEquals("rfnd_cancel_450", response.razorpayRefundId());

        verify(membershipService).cancelMembership(userEmail);
        verify(paymentRepository).save(argThat(p ->
                p.getTransactionType() == TransactionType.REFUND &&
                p.getAmount() == 45000L &&
                "rfnd_cancel_450".equals(p.getRazorpayRefundId())
        ));
    }

    @Test
    void testCancelMembershipWithRefund_unpaidFinesExceedDeposit_zeroRefund() {
        Membership activeMem = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(activeMem));

        String origPaymentUuid = UUID.randomUUID().toString();
        Payment origPayment = Payment.builder()
                .uuid(origPaymentUuid)
                .userUuid(userUuid)
                .razorpayPaymentId("pay_orig_500")
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.of(origPayment));

        // ₹600 unpaid fines (60000 paise) -> eligible refund is 0
        when(fineService.calculateTotalPendingFineForUser(userUuid)).thenReturn(BigDecimal.valueOf(600.00));
        when(paymentRepository.findByPaymentUuidAndTransactionType(origPaymentUuid, TransactionType.REFUND))
                .thenReturn(List.of());

        MembershipCancellationRefundResponse response = paymentService.cancelMembershipWithRefund(userEmail);

        assertTrue(response.cancelled());
        assertEquals(0L, response.refundAmount());
        assertNull(response.razorpayRefundId());
        verify(razorpayClientService, never()).refundPayment(anyString(), anyLong());
        verify(membershipService).cancelMembership(userEmail);
    }

    // ==========================================
    // 6. Permanent Deactivation Refund
    // ==========================================

    @Test
    void testProcessPermanentDeactivationRefund_unreturnedBookDeduction() {
        String origPaymentUuid = UUID.randomUUID().toString();
        Payment origPayment = Payment.builder()
                .uuid(origPaymentUuid)
                .userUuid(userUuid)
                .razorpayPaymentId("pay_deact_123")
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                eq(userUuid), eq(PaymentType.MEMBERSHIP), eq(TransactionType.PAYMENT), eq(PaymentStatus.SUCCESS)))
                .thenReturn(Optional.of(origPayment));

        when(paymentRepository.findByPaymentUuidAndTransactionType(origPaymentUuid, TransactionType.REFUND))
                .thenReturn(List.of());

        // 2 unreturned books -> deduction: 2 * 10000 = 20000 paise -> refund = 50000 - 20000 = 30000 paise (₹300)
        when(borrowRepository.countByUser_UuidAndReturnedDateIsNull(userUuid)).thenReturn(2L);
        when(razorpayClientService.refundPayment("pay_deact_123", 30000L)).thenReturn("rfnd_deact_300");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.processPermanentDeactivationRefund(userUuid);

        assertNotNull(response);
        assertEquals(30000L, response.amount());
        assertEquals("rfnd_deact_300", response.razorpayRefundId());
        assertEquals("REFUND", response.transactionType());
    }

    @Test
    void testProcessPermanentDeactivationRefund_unreturnedBooksExceedDeposit_neverNegative() {
        String origPaymentUuid = UUID.randomUUID().toString();
        Payment origPayment = Payment.builder()
                .uuid(origPaymentUuid)
                .userUuid(userUuid)
                .razorpayPaymentId("pay_deact_exceed")
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.of(origPayment));

        when(paymentRepository.findByPaymentUuidAndTransactionType(origPaymentUuid, TransactionType.REFUND))
                .thenReturn(List.of());

        // 6 unreturned books -> 60000 deduction -> refund = Math.max(0, 50000 - 60000) = 0
        when(borrowRepository.countByUser_UuidAndReturnedDateIsNull(userUuid)).thenReturn(6L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.processPermanentDeactivationRefund(userUuid);

        assertNotNull(response);
        assertEquals(0L, response.amount());
        assertNull(response.razorpayRefundId());
        verify(razorpayClientService, never()).refundPayment(anyString(), anyLong());
    }

    @Test
    void testProcessPermanentDeactivationRefund_noOriginalPayment_returnsNull() {
        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        PaymentResponseDto response = paymentService.processPermanentDeactivationRefund(userUuid);

        assertNull(response);
        verify(razorpayClientService, never()).refundPayment(anyString(), anyLong());
    }

    // ==========================================
    // 7. Get User Payments
    // ==========================================

    @Test
    void testGetUserPayments() {
        Payment p1 = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentRepository.findByUserUuidOrderByCreatedAtDesc(userUuid)).thenReturn(List.of(p1));

        List<PaymentResponseDto> list = paymentService.getUserPayments(userEmail);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(50000L, list.get(0).amount());
    }

    // ==========================================
    // 8. Additional Edge Cases & Error Branches
    // ==========================================

    @Test
    void testFindUserByIdentifier_viaProviderId() {
        String auth0Sub = "auth0|12345";
        com.kovanlabs.librarymanagement.database.entity.UserProvider up =
                new com.kovanlabs.librarymanagement.database.entity.UserProvider();
        up.setUserUuid(userUuid);
        up.setProviderId(auth0Sub);

        when(userProviderRepository.findByProviderId(auth0Sub)).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));

        List<PaymentResponseDto> list = paymentService.getUserPayments(auth0Sub);
        assertNotNull(list);
    }

    @Test
    void testFindUserByIdentifier_notFound_throwsException() {
        when(userProviderRepository.findByProviderId("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> paymentService.getUserPayments("unknown"));
    }

    @Test
    void testCreateMembershipOrder_noApplicationFound_throwsBadRequest() {
        when(membershipService.hasActiveMembership(userUuid)).thenReturn(false);
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> paymentService.createMembershipOrder(userEmail));
    }

    @Test
    void testCreateFineOrder_fineNotFound_throwsNotFound() {
        when(fineRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> paymentService.createFineOrder(999L, userEmail));
    }

    @Test
    void testCreateFineOrder_zeroPendingAmount_throwsBadRequest() {
        Fine fine = Fine.builder()
                .id(104L)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PENDING)
                .build();

        when(fineRepository.findById(104L)).thenReturn(Optional.of(fine));

        assertThrows(ResponseStatusException.class, () -> paymentService.createFineOrder(104L, userEmail));
    }

    @Test
    void testCreateFineOrder_adminUserCanCreateForOthers() {
        User adminUser = User.builder()
                .uuid(UUID.randomUUID().toString())
                .email("admin@example.com")
                .role(RoleEnum.ADMIN)
                .build();
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));

        Fine otherUserFine = Fine.builder()
                .id(105L)
                .userUuid("someone-else")
                .pendingFineAmount(BigDecimal.valueOf(20.00))
                .status(FineStatus.PENDING)
                .build();

        when(fineRepository.findById(105L)).thenReturn(Optional.of(otherUserFine));
        when(razorpayClientService.createOrder(eq(2000L), anyString(), anyMap())).thenReturn("order_admin_fine");

        PaymentOrderResponse response = paymentService.createFineOrder(105L, "admin@example.com");
        assertNotNull(response);
        assertEquals("order_admin_fine", response.orderId());
    }

    @Test
    void testVerifyPayment_orderNotFound_throwsNotFound() {
        VerifyPaymentRequest request = new VerifyPaymentRequest("order_unknown", "pay_x", "sig_x");
        when(paymentRepository.findByRazorpayOrderId("order_unknown")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> paymentService.verifyPayment(request, userEmail));
    }

    @Test
    void testVerifyPayment_differentUserNotAdmin_throwsForbidden() {
        VerifyPaymentRequest request = new VerifyPaymentRequest("order_diff", "pay_diff", "sig_diff");
        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid("different-user")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_diff")).thenReturn(Optional.of(payment));

        assertThrows(ResponseStatusException.class, () -> paymentService.verifyPayment(request, userEmail));
    }

    @Test
    void testVerifyPayment_fineSettlementExceptionHandledGracefully() {
        String orderId = "order_f_err";
        String payId = "pay_f_err";
        String sig = "valid_sig";
        VerifyPaymentRequest request = new VerifyPaymentRequest(orderId, payId, sig);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.FINE)
                .transactionType(TransactionType.PAYMENT)
                .razorpayOrderId(orderId)
                .referenceId("106")
                .amount(2500L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(payment));
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("Simulated fine settlement error")).when(fineService).payFine(106L);

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);
        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
    }

    @Test
    void testHandleWebhook_refundProcessed() {
        String payload = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_hook_1\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .razorpayRefundId("rfnd_hook_1")
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_hook_1")).thenReturn(Optional.of(refund));

        paymentService.handleWebhook(payload, "sig");

        assertEquals(PaymentStatus.SUCCESS, refund.getStatus());
        verify(paymentRepository).save(refund);
    }

    @Test
    void testHandleWebhook_fineOrderPaid() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_web_fine\",\"id\":\"pay_web_fine\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.FINE)
                .referenceId("107")
                .razorpayOrderId("order_web_fine")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_web_fine")).thenReturn(Optional.of(payment));

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(fineService).payFine(107L);
    }

    @Test
    void testHandleWebhook_fineSettlementExceptionHandled() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_web_fine_err\",\"id\":\"pay_web_fine_err\"}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentType(PaymentType.FINE)
                .referenceId("108")
                .razorpayOrderId("order_web_fine_err")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_web_fine_err")).thenReturn(Optional.of(payment));
        doThrow(new RuntimeException("Fine settlement failed")).when(fineService).payFine(108L);

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    void testHandleWebhook_malformedJson_throwsBadRequest() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> paymentService.handleWebhook("not-json", "sig"));
    }

    @Test
    void testCancelMembershipWithRefund_noActiveMembership_throwsBadRequest() {
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> paymentService.cancelMembershipWithRefund(userEmail));
    }

    @Test
    void testCancelMembershipWithRefund_noOriginalPayment_cancelsWithoutRefund() {
        Membership activeMem = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(activeMem));

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        when(fineService.calculateTotalPendingFineForUser(userUuid)).thenReturn(BigDecimal.ZERO);

        MembershipCancellationRefundResponse response = paymentService.cancelMembershipWithRefund(userEmail);

        assertTrue(response.cancelled());
        assertEquals(50000L, response.refundAmount());
        assertNull(response.razorpayRefundId());
        verify(membershipService).cancelMembership(userEmail);
    }
}
