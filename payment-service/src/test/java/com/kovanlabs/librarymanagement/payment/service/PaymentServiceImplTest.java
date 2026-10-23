package com.kovanlabs.librarymanagement.payment.service;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.*;
import com.kovanlabs.librarymanagement.database.repository.*;
import com.kovanlabs.librarymanagement.payment.config.RazorpayProperties;
import com.kovanlabs.librarymanagement.payment.dto.MembershipCancellationRefundResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentHistoryResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.PaymentOrderResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.RazorpayOrderDetails;
import com.kovanlabs.librarymanagement.payment.dto.VerifyPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private RazorpayClientService razorpayClientService;

    @Mock
    private RazorpayProperties razorpayProperties;

    @Mock
    private com.kovanlabs.librarymanagement.aws.s3.service.S3Service s3Service;

    @Mock
    private PaymentDocumentGenerator paymentDocumentGenerator;

    @Spy
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

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
        when(membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.empty());

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

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testCreateMembershipOrder_alreadyActive_throwsBadRequest() {
        Membership active = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusMonths(6))
                .build();
        when(membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.of(active));

        assertThrows(ResponseStatusException.class, () -> paymentService.createMembershipOrder(userEmail));
    }

    @Test
    void testCreateMembershipOrder_notSigned_throwsBadRequest() {
        when(membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.empty());

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

        verify(paymentRepository, never()).save(any());
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

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(true);
        when(razorpayClientService.fetchOrder(orderId)).thenReturn(
                new RazorpayOrderDetails(orderId, 50000L, Map.of("user_uuid", userUuid, "payment_type", "MEMBERSHIP"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Membership pendingMem = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(pendingMem));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertEquals(payId, response.razorpayPaymentId());
        assertEquals(MembershipStatus.ACTIVE, pendingMem.getStatus());
        verify(membershipRepository).save(pendingMem);
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.SUCCESS && p.getAmount() == 50000L));
    }

    @Test
    void testVerifyPayment_fineSuccess() {
        String orderId = "order_f_1";
        String payId = "pay_f_1";
        String sig = "valid_sig";
        VerifyPaymentRequest request = new VerifyPaymentRequest(orderId, payId, sig);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(true);
        when(razorpayClientService.fetchOrder(orderId)).thenReturn(
                new RazorpayOrderDetails(orderId, 2500L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "101"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Fine fine = Fine.builder()
                .id(101L)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.valueOf(25.00))
                .status(FineStatus.PENDING)
                .build();
        when(fineRepository.findById(101L)).thenReturn(Optional.of(fine));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        assertEquals(FineStatus.PAID, fine.getStatus());
        assertEquals(BigDecimal.ZERO, fine.getPendingFineAmount());
        verify(fineRepository).save(fine);
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.SUCCESS && p.getAmount() == 2500L));
    }

    @Test
    void testVerifyPayment_invalidSignature_recordsAsFailed() {
        String orderId = "order_m_2";
        String payId = "pay_m_2";
        String sig = "invalid_sig";
        VerifyPaymentRequest request = new VerifyPaymentRequest(orderId, payId, sig);

        when(paymentRepository.findByRazorpayPaymentId(payId)).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(false);
        when(razorpayClientService.fetchOrder(orderId)).thenReturn(
                new RazorpayOrderDetails(orderId, 50000L, Map.of("user_uuid", userUuid, "payment_type", "MEMBERSHIP"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertEquals("FAILED", response.status());
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
        verify(membershipRepository, never()).save(any());
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
        verify(membershipRepository, never()).save(any());
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
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_web_1")).thenReturn(Optional.of(payment));

        Membership pendingMem = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(pendingMem));

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("pay_web_1", payment.getRazorpayPaymentId());
        verify(paymentRepository).save(payment);
        assertEquals(MembershipStatus.ACTIVE, pendingMem.getStatus());
        verify(membershipRepository).save(pendingMem);
    }

    @Test
    void testHandleWebhook_paymentCaptured_createsNewRecordViaMapper() {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_web_new\",\"id\":\"pay_web_new\",\"amount\":50000,\"notes\":{\"user_uuid\":\"" + userUuid + "\",\"payment_type\":\"MEMBERSHIP\"}}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.findByRazorpayOrderId("order_web_new")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Membership pendingMem = Membership.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .build();
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(pendingMem));

        paymentService.handleWebhook(payload, "good_sig");

        verify(paymentRepository).save(argThat(p ->
                p.getStatus() == PaymentStatus.SUCCESS &&
                "order_web_new".equals(p.getRazorpayOrderId()) &&
                "pay_web_new".equals(p.getRazorpayPaymentId()) &&
                p.getTransactionType() == TransactionType.PAYMENT &&
                p.getAmount() == 50000L
        ));
        assertEquals(MembershipStatus.ACTIVE, pendingMem.getStatus());
        verify(membershipRepository).save(pendingMem);
    }

    @Test
    void testHandleWebhook_paymentFailed() {
        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_fail_1\",\"id\":\"pay_fail_1\",\"notes\":{\"user_uuid\":\"" + userUuid + "\",\"payment_type\":\"MEMBERSHIP\"}}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.findByRazorpayOrderId("order_fail_1")).thenReturn(Optional.empty());

        paymentService.handleWebhook(payload, "good_sig");

        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED && "order_fail_1".equals(p.getRazorpayOrderId())));
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

        verify(membershipRepository, never()).save(any());
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
                .status(PaymentStatus.SUCCESS)
                .build();

        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .paymentUuid(origPaymentUuid)
                .razorpayRefundId("rfnd_failed_1")
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.SUCCESS)
                .amount(50000L)
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_failed_1")).thenReturn(Optional.of(refund));

        paymentService.handleWebhook(payload, "sig");

        assertEquals(PaymentStatus.FAILED, refund.getStatus());
        verify(paymentRepository).save(refund);
        verify(paymentRepository, never()).save(origPayment);
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

        Fine pendingFine = Fine.builder()
                .id(99L)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.valueOf(50.00)) // ₹50 unpaid fine = 5000 paise
                .status(FineStatus.PENDING)
                .build();
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING))
                .thenReturn(List.of(pendingFine));

        when(paymentRepository.findByPaymentUuidAndTransactionType(origPaymentUuid, TransactionType.REFUND))
                .thenReturn(List.of());
        when(razorpayClientService.refundPayment("pay_orig_123", 45000L)).thenReturn("rfnd_cancel_450");

        MembershipCancellationRefundResponse response = paymentService.cancelMembershipWithRefund(userEmail);

        assertTrue(response.cancelled());
        assertEquals(50000L, response.originalDepositAmount());
        assertEquals(5000L, response.unpaidFinesDeducted());
        assertEquals(45000L, response.refundAmount());
        assertEquals("rfnd_cancel_450", response.razorpayRefundId());

        assertEquals(MembershipStatus.CANCELLED, activeMem.getStatus());
        verify(membershipRepository).save(activeMem);
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

        Fine largeFine = Fine.builder()
                .id(100L)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.valueOf(600.00)) // ₹600 fines
                .status(FineStatus.PENDING)
                .build();
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING))
                .thenReturn(List.of(largeFine));

        when(paymentRepository.findByPaymentUuidAndTransactionType(origPaymentUuid, TransactionType.REFUND))
                .thenReturn(List.of());

        MembershipCancellationRefundResponse response = paymentService.cancelMembershipWithRefund(userEmail);

        assertTrue(response.cancelled());
        assertEquals(0L, response.refundAmount());
        assertNull(response.razorpayRefundId());
        verify(razorpayClientService, never()).refundPayment(anyString(), anyLong());
        assertEquals(MembershipStatus.CANCELLED, activeMem.getStatus());
        verify(membershipRepository).save(activeMem);
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
        UserProvider up = new UserProvider();
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
        when(membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.empty());
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
        when(razorpayClientService.fetchOrder("order_unknown"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        assertThrows(ResponseStatusException.class, () -> paymentService.verifyPayment(request, userEmail));
    }

    @Test
    void testVerifyPayment_differentUserNotAdmin_throwsForbidden() {
        VerifyPaymentRequest request = new VerifyPaymentRequest("order_diff", "pay_diff", "sig_diff");
        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid("different-user")
                .status(PaymentStatus.SUCCESS)
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

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature(orderId, payId, sig)).thenReturn(true);
        when(razorpayClientService.fetchOrder(orderId)).thenReturn(
                new RazorpayOrderDetails(orderId, 2500L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "106"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        when(fineRepository.findById(106L)).thenThrow(new RuntimeException("Simulated fine settlement error"));

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
                .status(PaymentStatus.FAILED)
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
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_web_fine")).thenReturn(Optional.of(payment));

        Fine fine = Fine.builder()
                .id(107L)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.valueOf(25.00))
                .status(FineStatus.PENDING)
                .build();
        when(fineRepository.findById(107L)).thenReturn(Optional.of(fine));

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(FineStatus.PAID, fine.getStatus());
        verify(fineRepository).save(fine);
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
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_web_fine_err")).thenReturn(Optional.of(payment));
        when(fineRepository.findById(108L)).thenThrow(new RuntimeException("Fine settlement failed"));

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

        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING))
                .thenReturn(List.of());

        MembershipCancellationRefundResponse response = paymentService.cancelMembershipWithRefund(userEmail);

        assertTrue(response.cancelled());
        assertEquals(50000L, response.refundAmount());
        assertNull(response.razorpayRefundId());
        assertEquals(MembershipStatus.CANCELLED, activeMem.getStatus());
        verify(membershipRepository).save(activeMem);
    }

    // ==========================================
    // 6. Payment History & Receipt Tests
    // ==========================================

    @Test
    void testGetPaymentHistory_success_withSummaryAndRefundDetails() {
        Payment origPayment = Payment.builder()
                .uuid("p-1")
                .userUuid(userUuid)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Payment refundPayment = Payment.builder()
                .uuid("r-1")
                .userUuid(userUuid)
                .transactionType(TransactionType.REFUND)
                .paymentType(PaymentType.MEMBERSHIP)
                .paymentUuid("p-1")
                .amount(20000L)
                .status(PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(paymentRepository.findByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(List.of(origPayment, refundPayment));

        Page<Payment> page = new PageImpl<>(List.of(origPayment), Pageable.unpaged(), 1);
        when(paymentRepository.findPaymentHistory(eq(userUuid), any(), any(), any(), any()))
                .thenReturn(page);
        when(paymentRepository.findByPaymentUuidAndTransactionType("p-1", TransactionType.REFUND))
                .thenReturn(List.of(refundPayment));

        PaymentHistoryResponseDto response = paymentService.getPaymentHistory(
                userEmail, PaymentType.MEMBERSHIP, PaymentStatus.SUCCESS, null, 0, 10
        );

        assertNotNull(response);
        assertEquals(50000L, response.totalPaid());
        assertEquals(20000L, response.totalRefunded());
        assertEquals(30000L, response.netPaid());
        assertEquals(2L, response.successfulTransactionCount());
        assertEquals(1, response.content().size());
        assertEquals("p-1", response.content().get(0).uuid());
        assertEquals(1, response.content().get(0).refunds().size());
        assertEquals("r-1", response.content().get(0).refunds().get(0).uuid());
    }


    @Test
    void testHandleWebhook_refundProcessed_updatesOriginalPaymentAccurately() {
        String payload = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_123\",\"payment_id\":\"pay_orig_1\",\"amount\":25000}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment origPayment = Payment.builder()
                .uuid("orig-uuid-1")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .razorpayPaymentId("pay_orig_1")
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        Payment refundPayment = Payment.builder()
                .uuid("rfnd-uuid-1")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.REFUND)
                .razorpayRefundId("rfnd_123")
                .paymentUuid("orig-uuid-1")
                .amount(25000L)
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_123")).thenReturn(Optional.of(refundPayment));

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.SUCCESS, refundPayment.getStatus());
        assertEquals(PaymentStatus.SUCCESS, origPayment.getStatus());
        verify(paymentRepository).save(refundPayment);
        verify(paymentRepository, never()).save(origPayment);
    }

    @Test
    void testHandleWebhook_refundProcessed_fullRefund_setsStatusRefunded() {
        String payload = "{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":{\"id\":\"rfnd_full\",\"payment_id\":\"pay_orig_2\",\"amount\":50000}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);

        Payment origPayment = Payment.builder()
                .uuid("orig-uuid-2")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .razorpayPaymentId("pay_orig_2")
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        Payment refundPayment = Payment.builder()
                .uuid("rfnd-uuid-2")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.REFUND)
                .razorpayRefundId("rfnd_full")
                .paymentUuid("orig-uuid-2")
                .amount(50000L)
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_full")).thenReturn(Optional.of(refundPayment));

        paymentService.handleWebhook(payload, "good_sig");

        assertEquals(PaymentStatus.SUCCESS, refundPayment.getStatus());
        assertEquals(PaymentStatus.SUCCESS, origPayment.getStatus());
        verify(paymentRepository).save(refundPayment);
        verify(paymentRepository, never()).save(origPayment);
    }

    @Test
    void testHandleWebhook_paymentFailed_fetchesOrderNotesWhenNotesMissing() {
        String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_fail_fetch\",\"id\":\"pay_fail_fetch\",\"notes\":{}}}}}";
        when(razorpayClientService.verifyWebhookSignature(anyString(), anyString())).thenReturn(true);
        when(paymentRepository.findByRazorpayPaymentId("pay_fail_fetch")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_fail_fetch")).thenReturn(Optional.empty());
        when(razorpayClientService.fetchOrder("order_fail_fetch")).thenReturn(
                new RazorpayOrderDetails("order_fail_fetch", 33500L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "99"))
        );

        paymentService.handleWebhook(payload, "good_sig");

        verify(paymentRepository).save(argThat(p ->
                p.getStatus() == PaymentStatus.FAILED &&
                "order_fail_fetch".equals(p.getRazorpayOrderId()) &&
                "pay_fail_fetch".equals(p.getRazorpayPaymentId()) &&
                p.getAmount() == 33500L &&
                p.getPaymentType() == PaymentType.FINE
        ));
    }

    @Test
    void testVerifyPayment_failureSignatureNull_recordsAsFailed() {
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                "order_fail_rf_1",
                "pay_fail_rf_1",
                null
        );

        when(paymentRepository.findByRazorpayPaymentId("pay_fail_rf_1")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_fail_rf_1")).thenReturn(Optional.empty());
        when(razorpayClientService.fetchOrder("order_fail_rf_1")).thenReturn(
                new RazorpayOrderDetails("order_fail_rf_1", 33500L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "88"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("FAILED", response.status());
        assertEquals("order_fail_rf_1", response.razorpayOrderId());
        assertEquals("pay_fail_rf_1", response.razorpayPaymentId());
        assertEquals(33500L, response.amount());
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void testVerifyPayment_failureInvalidSignature_recordsAsFailed() {
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                "order_fail_rf_2",
                "pay_fail_rf_2",
                "invalid_sig"
        );

        when(razorpayClientService.verifyPaymentSignature("order_fail_rf_2", "pay_fail_rf_2", "invalid_sig")).thenReturn(false);
        when(paymentRepository.findByRazorpayPaymentId("pay_fail_rf_2")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_fail_rf_2")).thenReturn(Optional.empty());
        when(razorpayClientService.fetchOrder("order_fail_rf_2")).thenReturn(
                new RazorpayOrderDetails("order_fail_rf_2", 33500L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "88"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("FAILED", response.status());
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.FAILED));
    }

    @Test
    void testVerifyPayment_existingPaymentUpdatedToFailed() {
        Payment existing = Payment.builder()
                .uuid("pay-uuid-existing")
                .userUuid(userUuid)
                .paymentType(PaymentType.FINE)
                .razorpayOrderId("order_fail_exist")
                .status(PaymentStatus.FAILED)
                .amount(33500L)
                .build();

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                "order_fail_exist",
                "pay_fail_new_id",
                null
        );

        when(paymentRepository.findByRazorpayPaymentId("pay_fail_new_id")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_fail_exist")).thenReturn(Optional.of(existing));
        when(razorpayClientService.fetchOrder("order_fail_exist")).thenReturn(
                new RazorpayOrderDetails("order_fail_exist", 33500L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "88"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("FAILED", response.status());
        assertEquals("pay_fail_new_id", existing.getRazorpayPaymentId());
        verify(paymentRepository).save(existing);
    }

    @Test
    void testVerifyPayment_alreadySuccess_doesNotOverwriteWithFailure() {
        Payment existing = Payment.builder()
                .uuid("pay-uuid-success")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_success_1")
                .status(PaymentStatus.SUCCESS)
                .amount(50000L)
                .build();

        VerifyPaymentRequest request = new VerifyPaymentRequest(
                "order_success_1",
                "pay_late_fail",
                null
        );

        when(paymentRepository.findByRazorpayPaymentId("pay_late_fail")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_success_1")).thenReturn(Optional.of(existing));

        PaymentResponseDto response = paymentService.verifyPayment(request, userEmail);

        assertNotNull(response);
        assertEquals("SUCCESS", response.status());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testDownloadInvoice_existingS3Document() {
        Payment payment = Payment.builder()
                .uuid("pay-uuid-doc-1")
                .userUuid(userUuid)
                .documentNumber("REC-202610-001")
                .pdfUrl("https://invoice-lms.s3.ap-south-1.amazonaws.com/invoices/pay-uuid-doc-1/REC-202610-001.pdf")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .amount(50000L)
                .build();

        byte[] expectedBytes = "SAMPLE_S3_PDF".getBytes();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-uuid-doc-1")).thenReturn(Optional.of(payment));
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-202610-001");
        when(s3Service.downloadFile(any(), any(), eq("invoices/Test_Member/REC-202610-001.pdf"))).thenReturn(expectedBytes);

        byte[] result = paymentService.downloadInvoice("pay-uuid-doc-1", userEmail);

        assertNotNull(result);
        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void testDownloadInvoice_missingS3Document_generatesAndUploads() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-uuid-doc-2")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .amount(50000L)
                .build();

        byte[] generatedBytes = "GENERATED_PDF".getBytes();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-uuid-doc-2")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType("pay-uuid-doc-2", TransactionType.REFUND)).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-202610-002");
        when(paymentDocumentGenerator.generatePdf(eq(payment), eq(user), anyList(), eq("REC-202610-002"))).thenReturn(generatedBytes);
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-uuid-doc-2", userEmail);

        assertNotNull(result);
        assertArrayEquals(generatedBytes, result);
        verify(s3Service).uploadFileBytes(any(), any(), eq("invoices/Test_Member/REC-202610-002.pdf"), eq(generatedBytes), eq("application/pdf"));
        verify(paymentRepository).save(payment);
        assertNotNull(payment.getPdfUrl());
        assertEquals("REC-202610-002", payment.getDocumentNumber());
    }

    @Test
    void testDownloadInvoice_unauthorizedUser_throwsForbidden() {
        Payment payment = Payment.builder()
                .uuid("pay-uuid-doc-3")
                .userUuid("other-user-uuid")
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-uuid-doc-3")).thenReturn(Optional.of(payment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.downloadInvoice("pay-uuid-doc-3", userEmail));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testDownloadInvoice_adminAccessOtherUserPayment_success() throws Exception {
        User adminUser = User.builder()
                .uuid("admin-uuid")
                .email("admin@example.com")
                .role(RoleEnum.ADMIN)
                .build();

        Payment payment = Payment.builder()
                .uuid("pay-uuid-doc-4")
                .userUuid("other-user-uuid")
                .status(PaymentStatus.FAILED)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.FINE)
                .amount(2500L)
                .build();

        byte[] generatedBytes = "FAILED_STMT_PDF".getBytes();

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(paymentRepository.findByUuid("pay-uuid-doc-4")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid("other-user-uuid")).thenReturn(Optional.empty());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("STMT-202610-004");
        when(paymentDocumentGenerator.generatePdf(eq(payment), any(), anyList(), eq("STMT-202610-004"))).thenReturn(generatedBytes);
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-uuid-doc-4", "admin@example.com");

        assertNotNull(result);
        assertArrayEquals(generatedBytes, result);
    }

    @Test
    void testDownloadInvoice_paymentNotFound_throwsNotFound() {
        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("non-existent")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.downloadInvoice("non-existent", userEmail));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void testDownloadInvoice_lookupByNumericId() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-uuid-numeric-8")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .amount(50000L)
                .build();

        byte[] generatedBytes = "NUMERIC_ID_PDF".getBytes();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findById(8L)).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType("pay-uuid-numeric-8", TransactionType.REFUND)).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-202610-008");
        when(paymentDocumentGenerator.generatePdf(eq(payment), eq(user), anyList(), eq("REC-202610-008"))).thenReturn(generatedBytes);
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("8", userEmail);

        assertNotNull(result);
        assertArrayEquals(generatedBytes, result);
    }
}
