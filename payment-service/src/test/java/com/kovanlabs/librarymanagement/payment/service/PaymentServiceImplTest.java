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
        assertEquals(50000L, list.getFirst().amount());
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
        assertEquals("p-1", response.content().getFirst().uuid());
        assertEquals(1, response.content().getFirst().refunds().size());
        assertEquals("r-1", response.content().getFirst().refunds().getFirst().uuid());
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

    @Test
    void testCancelMembershipWithRefund_withExistingRefundsAndNoRefundEligible() {
        Membership membership = Membership.builder()
                .uuid("mem-uuid-cancel-2")
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();

        Payment originalPayment = Payment.builder()
                .uuid("orig-pay-cancel-2")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.SUCCESS)
                .amount(50000L)
                .razorpayPaymentId("pay_rzp_cancel_2")
                .build();

        Payment prevRefundSuccess = Payment.builder()
                .status(PaymentStatus.SUCCESS)
                .amount(20000L)
                .build();
        Payment prevRefundFailed = Payment.builder()
                .status(PaymentStatus.FAILED)
                .amount(10000L)
                .build();

        Fine fine = Fine.builder()
                .pendingFineAmount(BigDecimal.valueOf(400))
                .status(FineStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(membership));
        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(originalPayment));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING))
                .thenReturn(List.of(fine));
        when(paymentRepository.findByPaymentUuidAndTransactionType("orig-pay-cancel-2", TransactionType.REFUND))
                .thenReturn(List.of(prevRefundSuccess, prevRefundFailed));
        when(razorpayClientService.refundPayment("pay_rzp_cancel_2", 10000L)).thenReturn("rfnd_cancel_2");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipCancellationRefundResponse res = paymentService.cancelMembershipWithRefund(userEmail);

        assertNotNull(res);
        assertTrue(res.cancelled());
        assertEquals(10000L, res.refundAmount());
        assertEquals(MembershipStatus.CANCELLED, membership.getStatus());
    }

    @Test
    void testCancelMembershipWithRefund_unpaidFinesExceedDeposit_noRefundIssued() {
        Membership membership = Membership.builder()
                .uuid("mem-uuid-cancel-3")
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();

        Payment originalPayment = Payment.builder()
                .uuid("orig-pay-cancel-3")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.SUCCESS)
                .amount(50000L)
                .razorpayPaymentId("pay_rzp_cancel_3")
                .build();

        Fine bigFine = Fine.builder()
                .pendingFineAmount(BigDecimal.valueOf(600))
                .status(FineStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(membership));
        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(originalPayment));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING))
                .thenReturn(List.of(bigFine));
        when(paymentRepository.findByPaymentUuidAndTransactionType("orig-pay-cancel-3", TransactionType.REFUND))
                .thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipCancellationRefundResponse res = paymentService.cancelMembershipWithRefund(userEmail);

        assertNotNull(res);
        assertEquals(0L, res.refundAmount());
        verify(razorpayClientService, never()).refundPayment(anyString(), anyLong());
    }

    @Test
    void testProcessPermanentDeactivationRefund_withRefundFilterAndUnreturnedBooksDeduction() {
        Payment originalPayment = Payment.builder()
                .uuid("orig-deact-1")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.SUCCESS)
                .amount(50000L)
                .razorpayPaymentId("pay_deact_1")
                .build();

        Payment prevRefundSuccess = Payment.builder()
                .status(PaymentStatus.SUCCESS)
                .amount(10000L)
                .build();
        Payment prevRefundFailed = Payment.builder()
                .status(PaymentStatus.FAILED)
                .amount(5000L)
                .build();

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(originalPayment));
        when(paymentRepository.findByPaymentUuidAndTransactionType("orig-deact-1", TransactionType.REFUND))
                .thenReturn(List.of(prevRefundSuccess, prevRefundFailed));
        when(borrowRepository.countByUser_UuidAndReturnedDateIsNull(userUuid)).thenReturn(1L);
        when(razorpayClientService.refundPayment("pay_deact_1", 40000L)).thenReturn("rfnd_deact_1");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto res = paymentService.processPermanentDeactivationRefund(userUuid);

        assertNotNull(res);
        assertEquals("REFUND", res.transactionType());
        assertEquals(40000L, res.amount());
    }

    @Test
    void testProcessPermanentDeactivationRefund_nullOriginalPaymentOrNullPaymentId() {
        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.empty());

        assertNull(paymentService.processPermanentDeactivationRefund(userUuid));

        Payment noPayId = Payment.builder()
                .uuid("no-pay-id")
                .userUuid(userUuid)
                .razorpayPaymentId(null)
                .build();
        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(noPayId));

        assertNull(paymentService.processPermanentDeactivationRefund(userUuid));
    }

    @Test
    void testProcessPermanentDeactivationRefund_alreadyFullyRefunded() {
        Payment originalPayment = Payment.builder()
                .uuid("orig-deact-full")
                .userUuid(userUuid)
                .amount(50000L)
                .razorpayPaymentId("pay_deact_full")
                .build();

        Payment prevRefundSuccess = Payment.builder()
                .status(PaymentStatus.SUCCESS)
                .amount(50000L)
                .build();

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(originalPayment));
        when(paymentRepository.findByPaymentUuidAndTransactionType("orig-deact-full", TransactionType.REFUND))
                .thenReturn(List.of(prevRefundSuccess));

        assertNull(paymentService.processPermanentDeactivationRefund(userUuid));
    }

    @Test
    void testHandleWebhook_refundProcessed_createsNewRefundRecordWhenNotFound() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment origPayment = Payment.builder()
                .uuid("orig-pay-rfnd")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .amount(50000L)
                .razorpayPaymentId("pay_for_refund")
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_new_123")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayPaymentId("pay_for_refund")).thenReturn(Optional.of(origPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
        {
          "event": "refund.processed",
          "payload": {
            "refund": {
              "entity": {
                "id": "rfnd_new_123",
                "payment_id": "pay_for_refund",
                "amount": 20000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository).save(argThat(p -> "rfnd_new_123".equals(p.getRazorpayRefundId()) && p.getStatus() == PaymentStatus.SUCCESS));
    }

    @Test
    void testHandleWebhook_refundProcessed_alreadySuccess_doesNotSaveAgain() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existingRefund = Payment.builder()
                .uuid("rfnd-exist-uuid")
                .status(PaymentStatus.SUCCESS)
                .razorpayRefundId("rfnd_exist_123")
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_exist_123")).thenReturn(Optional.of(existingRefund));

        String payload = """
        {
          "event": "refund.processed",
          "payload": {
            "refund": {
              "entity": {
                "id": "rfnd_exist_123",
                "payment_id": "pay_xyz",
                "amount": 10000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(existingRefund);
    }

    @Test
    void testHandleWebhook_refundFailed_alreadyFailed_doesNotSaveAgain() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existingRefund = Payment.builder()
                .uuid("rfnd-fail-uuid")
                .status(PaymentStatus.FAILED)
                .razorpayRefundId("rfnd_fail_123")
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_fail_123")).thenReturn(Optional.of(existingRefund));

        String payload = """
        {
          "event": "refund.failed",
          "payload": {
            "refund": {
              "entity": {
                "id": "rfnd_fail_123"
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(existingRefund);
    }

    @Test
    void testHandleWebhook_paymentCaptured_existingSuccess_skips() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existing = Payment.builder()
                .uuid("exist-succ-uuid")
                .status(PaymentStatus.SUCCESS)
                .razorpayOrderId("order_already_succ")
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_already_succ")).thenReturn(Optional.of(existing));

        String payload = """
        {
          "event": "payment.captured",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_already_succ",
                "order_id": "order_already_succ",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(existing);
    }

    @Test
    void testHandleWebhook_paymentFailed_existingSuccessOrFailed_skips() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existing = Payment.builder()
                .uuid("exist-fail-uuid")
                .status(PaymentStatus.FAILED)
                .razorpayPaymentId("pay_already_fail")
                .build();

        when(paymentRepository.findByRazorpayPaymentId("pay_already_fail")).thenReturn(Optional.of(existing));

        String payload = """
        {
          "event": "payment.failed",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_already_fail",
                "order_id": "order_xyz",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(existing);
    }

    @Test
    void testHandleWebhook_unknownEvent_handledGracefully() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        String payload = "{\"event\": \"payment.authorized\", \"payload\": {}}";

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
    }

    @Test
    void testCreateFineOrder_adminAccessOtherUserFine_success() {
        User adminUser = User.builder()
                .uuid("admin-uuid")
                .email("admin@example.com")
                .role(RoleEnum.ADMIN)
                .build();

        Fine fine = Fine.builder()
                .id(999L)
                .userUuid("other-user-uuid")
                .pendingFineAmount(BigDecimal.valueOf(15))
                .status(FineStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(fineRepository.findById(999L)).thenReturn(Optional.of(fine));
        when(razorpayClientService.createOrder(eq(1500L), anyString(), anyMap())).thenReturn("order_admin_fine_999");

        PaymentOrderResponse res = paymentService.createFineOrder(999L, "admin@example.com");

        assertNotNull(res);
        assertEquals("order_admin_fine_999", res.orderId());
        assertEquals(1500L, res.amount());
    }

    @Test
    void testCreateFineOrder_nullOrZeroPendingFine_throwsBadRequest() {
        Fine nullFine = Fine.builder()
                .id(888L)
                .userUuid(userUuid)
                .pendingFineAmount(null)
                .status(FineStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(fineRepository.findById(888L)).thenReturn(Optional.of(nullFine));

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> paymentService.createFineOrder(888L, userEmail));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        Fine zeroFine = Fine.builder()
                .id(889L)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PENDING)
                .build();
        when(fineRepository.findById(889L)).thenReturn(Optional.of(zeroFine));

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> paymentService.createFineOrder(889L, userEmail));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    void testVerifyPayment_adminVerifyingOtherUserPayment_success() {
        User adminUser = User.builder()
                .uuid("admin-uuid")
                .email("admin@example.com")
                .role(RoleEnum.ADMIN)
                .build();

        VerifyPaymentRequest request = new VerifyPaymentRequest("order_admin_v", "pay_admin_v", "sig_admin_v");

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(paymentRepository.findByRazorpayPaymentId("pay_admin_v")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_admin_v")).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature("order_admin_v", "pay_admin_v", "sig_admin_v")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_admin_v")).thenReturn(
                new RazorpayOrderDetails("order_admin_v", 50000L, Map.of("user_uuid", "other-user", "payment_type", "MEMBERSHIP"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto res = paymentService.verifyPayment(request, "admin@example.com");

        assertNotNull(res);
        assertEquals("SUCCESS", res.status());
    }

    @Test
    void testVerifyPayment_zeroAmountFallbackToFineOrMembership() {
        VerifyPaymentRequest request = new VerifyPaymentRequest("order_zero_amt", "pay_zero_amt", "sig_zero_amt");

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_zero_amt")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_zero_amt")).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature("order_zero_amt", "pay_zero_amt", "sig_zero_amt")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_zero_amt")).thenReturn(
                new RazorpayOrderDetails("order_zero_amt", 0L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "44"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto res = paymentService.verifyPayment(request, userEmail);

        assertNotNull(res);
        assertEquals(0L, res.amount());
    }

    @Test
    void testFindPaymentByIdOrUuid_lookupByRazorpayIds() throws Exception {
        Payment paymentByPayId = Payment.builder()
                .uuid("pay-uuid-rp-1")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay_rzp_999")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayPaymentId("pay_rzp_999")).thenReturn(Optional.of(paymentByPayId));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(any())).thenReturn("REC-999");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] bytes = paymentService.downloadInvoice("pay_rzp_999", userEmail);
        assertNotNull(bytes);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.downloadInvoice("   ", userEmail));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testDownloadInvoice_s3DownloadFails_regeneratesAndUploads() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-s3-fail")
                .userUuid(userUuid)
                .pdfUrl("https://s3.example.com/inv.pdf")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .build();

        byte[] regeneratedBytes = "REGENERATED_PDF".getBytes();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-s3-fail")).thenReturn(Optional.of(payment));
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-S3-FAIL");
        when(s3Service.downloadFile(any(), any(), anyString())).thenThrow(new RuntimeException("S3 bucket down"));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType("pay-s3-fail", TransactionType.REFUND)).thenReturn(List.of());
        when(paymentDocumentGenerator.generatePdf(eq(payment), eq(user), anyList(), eq("REC-S3-FAIL"))).thenReturn(regeneratedBytes);
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-s3-fail", userEmail);

        assertNotNull(result);
        assertArrayEquals(regeneratedBytes, result);
    }

    @Test
    void testDownloadInvoice_pdfGenerationFails_throwsInternalServerError() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-gen-err")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-gen-err")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-ERR");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString()))
                .thenThrow(new RuntimeException("OpenHtmlToPdf error"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.downloadInvoice("pay-gen-err", userEmail));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    @Test
    void testDownloadInvoice_s3UploadFails_stillReturnsPdfBytes() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-s3-up-fail")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        byte[] pdfBytes = "PDF_BYTES".getBytes();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-s3-up-fail")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-UP-FAIL");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn(pdfBytes);
        doThrow(new RuntimeException("S3 upload timeout")).when(s3Service).uploadFileBytes(any(), any(), anyString(), any(), anyString());

        byte[] result = paymentService.downloadInvoice("pay-s3-up-fail", userEmail);

        assertNotNull(result);
        assertArrayEquals(pdfBytes, result);
    }

    @Test
    void testGetPaymentHistory_filteringBySearchAndTypes() {
        Payment payment1 = Payment.builder()
                .uuid("pay-h-1")
                .userUuid(userUuid)
                .transactionType(TransactionType.PAYMENT)
                .razorpayOrderId("order_search_match")
                .razorpayPaymentId("pay_search_1")
                .paymentType(PaymentType.FINE)
                .status(PaymentStatus.SUCCESS)
                .amount(2500L)
                .createdAt(LocalDateTime.now())
                .build();

        Payment refundItem = Payment.builder()
                .uuid("rf-h-1")
                .paymentUuid("pay-h-1")
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.SUCCESS)
                .amount(500L)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUserUuidOrderByCreatedAtDesc(userUuid)).thenReturn(List.of(payment1, refundItem));

        Page<Payment> page = new PageImpl<>(List.of(payment1), Pageable.unpaged(), 1);
        when(paymentRepository.findPaymentHistory(eq(userUuid), eq(PaymentType.FINE), eq(PaymentStatus.SUCCESS), eq("order_search"), any()))
                .thenReturn(page);
        when(paymentRepository.findByPaymentUuidAndTransactionType("pay-h-1", TransactionType.REFUND))
                .thenReturn(List.of(refundItem));

        PaymentHistoryResponseDto res = paymentService.getPaymentHistory(
                userEmail, PaymentType.FINE, PaymentStatus.SUCCESS, "order_search", 0, 10);

        assertNotNull(res);
        assertEquals(1, res.content().size());
        assertEquals(2500L, res.totalPaid());
        assertEquals(500L, res.totalRefunded());
        assertEquals(2000L, res.netPaid());
    }

    @Test
    void testActivateMembershipInternal_alreadyActive_doesNotUpdate() {
        Membership activeMem = Membership.builder()
                .uuid("mem-active-uuid")
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusMonths(6))
                .build();

        Payment origPayment = Payment.builder()
                .uuid("pay-mem-act")
                .userUuid(userUuid)
                .status(PaymentStatus.FAILED)
                .paymentType(PaymentType.MEMBERSHIP)
                .build();

        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(activeMem));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        VerifyPaymentRequest req = new VerifyPaymentRequest("order_act", "pay_act", "sig_act");
        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_act")).thenReturn(Optional.of(origPayment));
        when(razorpayClientService.verifyPaymentSignature("order_act", "pay_act", "sig_act")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_act")).thenReturn(
                new RazorpayOrderDetails("order_act", 50000L, Map.of("user_uuid", userUuid, "payment_type", "MEMBERSHIP"))
        );

        PaymentResponseDto res = paymentService.verifyPayment(req, userEmail);
        assertNotNull(res);
        assertEquals("SUCCESS", res.status());
        verify(membershipRepository, never()).save(activeMem);
    }

    @Test
    void testSettleFineInternal_invalidReferenceId_handledGracefully() {
        Payment origPayment = Payment.builder()
                .uuid("pay-fine-invalid")
                .userUuid(userUuid)
                .status(PaymentStatus.FAILED)
                .paymentType(PaymentType.FINE)
                .referenceId("not-a-number")
                .build();

        VerifyPaymentRequest req = new VerifyPaymentRequest("order_fine_inv", "pay_fine_inv", "sig_fine_inv");
        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_fine_inv")).thenReturn(Optional.of(origPayment));
        when(razorpayClientService.verifyPaymentSignature("order_fine_inv", "pay_fine_inv", "sig_fine_inv")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_fine_inv")).thenReturn(
                new RazorpayOrderDetails("order_fine_inv", 1500L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "not-a-number"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto res = paymentService.verifyPayment(req, userEmail);
        assertNotNull(res);
        assertEquals("SUCCESS", res.status());
        verify(fineRepository, never()).save(any());
    }

    @Test
    void testCreateMembershipOrder_activeMembershipNullExpiry_throwsBadRequest() {
        Membership activeNullExpiry = Membership.builder()
                .uuid("mem-null-exp")
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .expiryDate(null)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.of(activeNullExpiry));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.createMembershipOrder(userEmail));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("User already has an active library membership", ex.getReason());
    }

    @Test
    void testCreateMembershipOrder_pendingMembershipNotFound_throwsBadRequest() {
        Membership expiredMem = Membership.builder()
                .uuid("mem-expired")
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .expiryDate(LocalDate.now().minusDays(10))
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.of(expiredMem));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.createMembershipOrder(userEmail));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("No membership application found. Please apply first.", ex.getReason());
    }

    @Test
    void testVerifyPayment_existingPaymentOtherUser_adminAllowed() {
        User adminUser = User.builder()
                .uuid("admin-uuid")
                .email("admin@example.com")
                .role(RoleEnum.ADMIN)
                .build();

        Payment existing = Payment.builder()
                .uuid("pay-existing-admin")
                .userUuid("other-user-uuid")
                .status(PaymentStatus.FAILED)
                .paymentType(PaymentType.FINE)
                .referenceId("10")
                .build();

        VerifyPaymentRequest req = new VerifyPaymentRequest("order_ex_admin", "pay_ex_admin", "sig_ex_admin");

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(paymentRepository.findByRazorpayPaymentId("pay_ex_admin")).thenReturn(Optional.of(existing));
        when(razorpayClientService.verifyPaymentSignature("order_ex_admin", "pay_ex_admin", "sig_ex_admin")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_ex_admin")).thenReturn(
                new RazorpayOrderDetails("order_ex_admin", 2500L, Map.of("user_uuid", "other-user-uuid", "payment_type", "FINE", "reference_id", "10"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto res = paymentService.verifyPayment(req, "admin@example.com");
        assertNotNull(res);
        assertEquals("SUCCESS", res.status());
    }

    @Test
    void testVerifyPayment_existingPaymentOtherUser_nonAdminThrowsForbidden() {
        Payment existing = Payment.builder()
                .uuid("pay-existing-other")
                .userUuid("different-user-uuid")
                .status(PaymentStatus.FAILED)
                .build();

        VerifyPaymentRequest req = new VerifyPaymentRequest("order_diff", "pay_diff", "sig_diff");

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_diff")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.verifyPayment(req, userEmail));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testVerifyPayment_orderNotesHasFineIdAndZeroAmountMembershipDefault() {
        VerifyPaymentRequest req = new VerifyPaymentRequest("order_default_amt", "pay_default_amt", "sig_def");

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_default_amt")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_default_amt")).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature(any(), any(), any())).thenReturn(false);
        when(razorpayClientService.fetchOrder("order_default_amt")).thenReturn(
                new RazorpayOrderDetails("order_default_amt", 0L, null)
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto res = paymentService.verifyPayment(req, userEmail);
        assertNotNull(res);
        assertEquals("FAILED", res.status());
        assertEquals(PaymentServiceImpl.MEMBERSHIP_FEE_PAISE, res.amount());
    }

    @Test
    void testHandleWebhook_orderPaid_finePaymentSettlesFine() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existingFinePayment = Payment.builder()
                .uuid("fine-pay-webhook")
                .userUuid(userUuid)
                .paymentType(PaymentType.FINE)
                .referenceId("77")
                .status(PaymentStatus.FAILED)
                .razorpayOrderId("order_fine_webhook")
                .build();

        Fine fine = Fine.builder()
                .id(77L)
                .status(FineStatus.PENDING)
                .pendingFineAmount(BigDecimal.valueOf(25))
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_fine_webhook")).thenReturn(Optional.of(existingFinePayment));
        when(fineRepository.findById(77L)).thenReturn(Optional.of(fine));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
        {
          "event": "order.paid",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_fine_webhook_id",
                "order_id": "order_fine_webhook",
                "amount": 2500
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        assertEquals(PaymentStatus.SUCCESS, existingFinePayment.getStatus());
        assertEquals("pay_fine_webhook_id", existingFinePayment.getRazorpayPaymentId());
        assertEquals(FineStatus.PAID, fine.getStatus());
    }

    @Test
    void testHandleWebhook_orderPaid_newPaymentWithFetchOrderAndFineIdNote() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        when(paymentRepository.findByRazorpayOrderId("order_new_wh")).thenReturn(Optional.empty());
        when(razorpayClientService.fetchOrder("order_new_wh")).thenReturn(
                new RazorpayOrderDetails("order_new_wh", 2500L, Map.of(
                        "user_uuid", userUuid,
                        "payment_type", "FINE",
                        "fine_id", "88"
                ))
        );

        Fine fine = Fine.builder()
                .id(88L)
                .status(FineStatus.PENDING)
                .pendingFineAmount(BigDecimal.valueOf(25))
                .build();
        when(fineRepository.findById(88L)).thenReturn(Optional.of(fine));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
        {
          "event": "order.paid",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_new_wh_id",
                "order_id": "order_new_wh",
                "amount": 0
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository).save(argThat(p -> p.getPaymentType() == PaymentType.FINE && p.getStatus() == PaymentStatus.SUCCESS));
        assertEquals(FineStatus.PAID, fine.getStatus());
    }

    @Test
    void testHandleWebhook_paymentFailed_foundByOrderIdWhenPaymentIdEmpty() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existing = Payment.builder()
                .uuid("pay-fail-order-id")
                .userUuid(userUuid)
                .status(null)
                .razorpayOrderId("order_fail_only")
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_fail_only")).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
        {
          "event": "payment.failed",
          "payload": {
            "payment": {
              "entity": {
                "id": "",
                "order_id": "order_fail_only",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        assertEquals(PaymentStatus.FAILED, existing.getStatus());
    }

    @Test
    void testHandleWebhook_refundProcessed_zeroRefundAmountUsesOriginalAmount() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment origPayment = Payment.builder()
                .uuid("orig-zero-rf")
                .userUuid(userUuid)
                .amount(50000L)
                .razorpayPaymentId("pay_orig_zero")
                .build();

        when(paymentRepository.findByRazorpayRefundId("rfnd_zero_amt")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayPaymentId("pay_orig_zero")).thenReturn(Optional.of(origPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
        {
          "event": "refund.processed",
          "payload": {
            "refund": {
              "entity": {
                "id": "rfnd_zero_amt",
                "payment_id": "pay_orig_zero",
                "amount": 0
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository).save(argThat(p -> p.getAmount() == 50000L && p.getStatus() == PaymentStatus.SUCCESS));
    }

    @Test
    void testHandleWebhook_refundProcessed_blankRefundIdOrEmptyPaymentId_skips() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        String payloadBlankRefund = """
        {
          "event": "refund.processed",
          "payload": {
            "refund": {
              "entity": {
                "id": "   "
              }
            }
          }
        }
        """;
        assertDoesNotThrow(() -> paymentService.handleWebhook(payloadBlankRefund, "good_sig"));

        String payloadNoOrigPayment = """
        {
          "event": "refund.processed",
          "payload": {
            "refund": {
              "entity": {
                "id": "rfnd_no_orig",
                "payment_id": "pay_not_in_db",
                "amount": 1000
              }
            }
          }
        }
        """;
        when(paymentRepository.findByRazorpayRefundId("rfnd_no_orig")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayPaymentId("pay_not_in_db")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> paymentService.handleWebhook(payloadNoOrigPayment, "good_sig"));
    }

    @Test
    void testHandleWebhook_refundFailed_blankRefundIdOrNotFound_skips() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        String payloadBlank = "{\"event\": \"refund.failed\", \"payload\": {\"refund\": {\"entity\": {\"id\": \"\"}}}}";
        assertDoesNotThrow(() -> paymentService.handleWebhook(payloadBlank, "good_sig"));

        String payloadNotFound = "{\"event\": \"refund.failed\", \"payload\": {\"refund\": {\"entity\": {\"id\": \"rfnd_not_found\"}}}}";
        when(paymentRepository.findByRazorpayRefundId("rfnd_not_found")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> paymentService.handleWebhook(payloadNotFound, "good_sig"));
    }

    @Test
    void testDownloadInvoice_existingPdfUrlReturnsEmptyBytes_regeneratesPdf() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-empty-s3")
                .userUuid(userUuid)
                .pdfUrl("https://s3.example.com/empty.pdf")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .build();

        byte[] regenerated = "REGENERATED".getBytes();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-empty-s3")).thenReturn(Optional.of(payment));
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-EMPTY-S3");
        when(s3Service.downloadFile(any(), any(), anyString())).thenReturn(new byte[0]);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generatePdf(eq(payment), eq(user), anyList(), eq("REC-EMPTY-S3"))).thenReturn(regenerated);
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-empty-s3", userEmail);
        assertNotNull(result);
        assertArrayEquals(regenerated, result);
    }

    @Test
    void testDownloadInvoice_refundTransactionType_doesNotQueryRefunds() throws Exception {
        Payment refund = Payment.builder()
                .uuid("pay-rf-doc")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.REFUND)
                .paymentType(PaymentType.MEMBERSHIP)
                .build();

        byte[] generated = "REFUND_DOC".getBytes();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-rf-doc")).thenReturn(Optional.of(refund));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentDocumentGenerator.generateDocumentNumber(refund)).thenReturn("REF-DOC-1");
        when(paymentDocumentGenerator.generatePdf(eq(refund), eq(user), eq(List.of()), eq("REF-DOC-1"))).thenReturn(generated);
        when(paymentRepository.save(refund)).thenReturn(refund);

        byte[] result = paymentService.downloadInvoice("pay-rf-doc", userEmail);
        assertNotNull(result);
        assertArrayEquals(generated, result);
        verify(paymentRepository, never()).findByPaymentUuidAndTransactionType(any(), any());
    }

    @Test
    void testDownloadInvoice_ownerBlankNameAndNoAtInEmail() throws Exception {
        User noAtUser = User.builder()
                .uuid("u-no-at")
                .name("   ")
                .email("simpleusername")
                .role(RoleEnum.USER)
                .build();

        Payment payment = Payment.builder()
                .uuid("pay-no-at")
                .userUuid("u-no-at")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId("simpleusername")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("simpleusername")).thenReturn(Optional.of(noAtUser));
        when(paymentRepository.findByUuid("pay-no-at")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid("u-no-at")).thenReturn(Optional.of(noAtUser));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-NO-AT");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-no-at", "simpleusername");
        assertNotNull(result);
        verify(s3Service).uploadFileBytes(any(), any(), eq("invoices/simpleusername/REC-NO-AT.pdf"), any(), anyString());
    }

    @Test
    void testFindUserByIdentifier_nullOrBlank_throwsUnauthorized() {
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> paymentService.getUserPayments(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex1.getStatusCode());

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> paymentService.getUserPayments("   "));
        assertEquals(HttpStatus.UNAUTHORIZED, ex2.getStatusCode());
    }

    @Test
    void testFindUserByIdentifier_providerPresentButUserUuidNotFound_fallsBackToEmail() {
        String auth0Sub = "auth0|sub123";
        UserProvider up = new UserProvider();
        up.setProviderId(auth0Sub);
        up.setUserUuid("non-existent-uuid");

        when(userProviderRepository.findByProviderId(auth0Sub)).thenReturn(Optional.of(up));
        when(userRepository.findByUuid("non-existent-uuid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(auth0Sub)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUserUuidOrderByCreatedAtDesc(userUuid)).thenReturn(List.of());

        List<PaymentResponseDto> list = paymentService.getUserPayments(auth0Sub);
        assertNotNull(list);
    }

    @Test
    void testCreateMembershipOrder_latestMembershipNotActive_noActiveFound() {
        Membership cancelledMem = Membership.builder()
                .uuid("mem-cancelled")
                .userUuid(userUuid)
                .status(MembershipStatus.CANCELLED)
                .build();

        Membership pendingSignedMem = Membership.builder()
                .uuid("mem-pending-signed")
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .signed(true)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.of(cancelledMem));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(pendingSignedMem));
        when(razorpayClientService.createOrder(eq(PaymentServiceImpl.MEMBERSHIP_FEE_PAISE), eq("mem_mem-pending-signed"), anyMap()))
                .thenReturn("order_mem_123");

        PaymentOrderResponse res = paymentService.createMembershipOrder(userEmail);
        assertNotNull(res);
        assertEquals("order_mem_123", res.orderId());
    }

    @Test
    void testVerifyPayment_newPaymentOrderBelongsToDifferentUser_forbidden() {
        VerifyPaymentRequest req = new VerifyPaymentRequest("order_diff_notes", "pay_diff_notes", "sig_diff");

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_diff_notes")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_diff_notes")).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature("order_diff_notes", "pay_diff_notes", "sig_diff")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_diff_notes")).thenReturn(
                new RazorpayOrderDetails("order_diff_notes", 50000L, Map.of(
                        "user_uuid", "other-user-uuid",
                        "payment_type", "MEMBERSHIP"
                ))
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.verifyPayment(req, userEmail));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void testVerifyPayment_existingPayment_nullPaymentId_savesWithoutSettingPaymentId() {
        Payment existing = Payment.builder()
                .uuid("pay-ex-null-pid")
                .userUuid(userUuid)
                .status(null)
                .razorpayOrderId("order_no_pid")
                .paymentType(PaymentType.MEMBERSHIP)
                .build();

        VerifyPaymentRequest req = new VerifyPaymentRequest("order_no_pid", null, null);

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayOrderId("order_no_pid")).thenReturn(Optional.of(existing));
        when(razorpayClientService.fetchOrder("order_no_pid")).thenReturn(
                new RazorpayOrderDetails("order_no_pid", 50000L, Map.of("user_uuid", userUuid, "payment_type", "MEMBERSHIP"))
        );
        when(paymentRepository.save(existing)).thenReturn(existing);

        PaymentResponseDto res = paymentService.verifyPayment(req, userEmail);
        assertNotNull(res);
        assertEquals("FAILED", res.status());
        assertNull(existing.getRazorpayPaymentId());
    }

    @Test
    void testVerifyPayment_membershipSuccess_noPendingMembership_internalDoesNothing() {
        VerifyPaymentRequest req = new VerifyPaymentRequest("order_no_mem", "pay_no_mem", "sig_mem");

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_no_mem")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_no_mem")).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature("order_no_mem", "pay_no_mem", "sig_mem")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_no_mem")).thenReturn(
                new RazorpayOrderDetails("order_no_mem", 50000L, Map.of("user_uuid", userUuid, "payment_type", "MEMBERSHIP"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.empty());

        PaymentResponseDto res = paymentService.verifyPayment(req, userEmail);
        assertNotNull(res);
        assertEquals("SUCCESS", res.status());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void testVerifyPayment_fineSuccess_fineNotFound_internalDoesNothing() {
        VerifyPaymentRequest req = new VerifyPaymentRequest("order_fine_nf", "pay_fine_nf", "sig_fine");

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByRazorpayPaymentId("pay_fine_nf")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_fine_nf")).thenReturn(Optional.empty());
        when(razorpayClientService.verifyPaymentSignature("order_fine_nf", "pay_fine_nf", "sig_fine")).thenReturn(true);
        when(razorpayClientService.fetchOrder("order_fine_nf")).thenReturn(
                new RazorpayOrderDetails("order_fine_nf", 2000L, Map.of("user_uuid", userUuid, "payment_type", "FINE", "reference_id", "55555"))
        );
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fineRepository.findById(55555L)).thenReturn(Optional.empty());

        PaymentResponseDto res = paymentService.verifyPayment(req, userEmail);
        assertNotNull(res);
        assertEquals("SUCCESS", res.status());
        verify(fineRepository, never()).save(any());
    }

    @Test
    void testHandleWebhook_paymentCaptured_blankOrNullOrderId_doesNothing() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        String payloadNullOrder = """
        {
          "event": "payment.captured",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_null_order"
              }
            }
          }
        }
        """;
        assertDoesNotThrow(() -> paymentService.handleWebhook(payloadNullOrder, "good_sig"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testHandleWebhook_paymentCaptured_existingPayment_nullPaymentIdInEntity() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existing = Payment.builder()
                .uuid("pay-wh-null-pid")
                .userUuid(userUuid)
                .status(null)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_wh_null_pid")
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_wh_null_pid")).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.empty());

        String payload = """
        {
          "event": "payment.captured",
          "payload": {
            "payment": {
              "entity": {
                "order_id": "order_wh_null_pid",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        assertEquals(PaymentStatus.SUCCESS, existing.getStatus());
        assertNull(existing.getRazorpayPaymentId());
    }

    @Test
    void testHandleWebhook_paymentCaptured_newPayment_notesDirectlyInPayload() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        when(paymentRepository.findByRazorpayOrderId("order_with_notes")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.empty());

        String payload = """
        {
          "event": "payment.captured",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_direct_notes",
                "order_id": "order_with_notes",
                "amount": 50000,
                "notes": {
                  "user_uuid": "%s",
                  "payment_type": "MEMBERSHIP",
                  "reference_id": "mem-direct"
                }
              }
            }
          }
        }
        """.formatted(userUuid);

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(razorpayClientService, never()).fetchOrder(anyString());
        verify(paymentRepository).save(argThat(p -> "order_with_notes".equals(p.getRazorpayOrderId()) && p.getStatus() == PaymentStatus.SUCCESS));
    }

    @Test
    void testHandleWebhook_paymentCaptured_newPayment_fetchOrderFails_skipsSaving() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        when(paymentRepository.findByRazorpayOrderId("order_fetch_fail")).thenReturn(Optional.empty());
        when(razorpayClientService.fetchOrder("order_fetch_fail")).thenThrow(new RuntimeException("Razorpay error"));

        String payload = """
        {
          "event": "payment.captured",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_fetch_fail",
                "order_id": "order_fetch_fail",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testHandleWebhook_paymentCaptured_newPayment_userUuidOrPaymentTypeStillNull_doesNotSave() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        when(paymentRepository.findByRazorpayOrderId("order_empty_notes")).thenReturn(Optional.empty());
        when(razorpayClientService.fetchOrder("order_empty_notes")).thenReturn(
                new RazorpayOrderDetails("order_empty_notes", 50000L, Map.of())
        );

        String payload = """
        {
          "event": "payment.captured",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_empty_notes",
                "order_id": "order_empty_notes",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testHandleWebhook_paymentFailed_blankOrNullOrderIdAndPaymentId_doesNothing() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        String payload = """
        {
          "event": "payment.failed",
          "payload": {
            "payment": {
              "entity": {
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testHandleWebhook_paymentFailed_existingPayment_nullPaymentId_savesWithoutSettingPaymentId() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        Payment existing = Payment.builder()
                .uuid("pay-fail-null-pid")
                .userUuid(userUuid)
                .status(null)
                .razorpayOrderId("order_fail_null_pid")
                .build();

        when(paymentRepository.findByRazorpayOrderId("order_fail_null_pid")).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
        {
          "event": "payment.failed",
          "payload": {
            "payment": {
              "entity": {
                "order_id": "order_fail_null_pid",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        assertEquals(PaymentStatus.FAILED, existing.getStatus());
        assertNull(existing.getRazorpayPaymentId());
    }

    @Test
    void testHandleWebhook_paymentFailed_newPayment_notesDirectlyInPayload() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        when(paymentRepository.findByRazorpayPaymentId("pay_fail_direct")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_fail_direct")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        String payload = """
        {
          "event": "payment.failed",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_fail_direct",
                "order_id": "order_fail_direct",
                "amount": 2500,
                "notes": {
                  "user_uuid": "%s",
                  "payment_type": "FINE",
                  "fine_id": "123"
                }
              }
            }
          }
        }
        """.formatted(userUuid);

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(razorpayClientService, never()).fetchOrder(anyString());
        verify(paymentRepository).save(argThat(p -> "order_fail_direct".equals(p.getRazorpayOrderId()) && p.getStatus() == PaymentStatus.FAILED));
    }

    @Test
    void testHandleWebhook_paymentFailed_newPayment_fetchOrderFails_skipsSaving() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);

        when(paymentRepository.findByRazorpayPaymentId("pay_fail_fetch_err")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_fail_fetch_err")).thenReturn(Optional.empty());
        when(razorpayClientService.fetchOrder("order_fail_fetch_err")).thenThrow(new RuntimeException("Order not found"));

        String payload = """
        {
          "event": "payment.failed",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_fail_fetch_err",
                "order_id": "order_fail_fetch_err",
                "amount": 50000
              }
            }
          }
        }
        """;

        assertDoesNotThrow(() -> paymentService.handleWebhook(payload, "good_sig"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testHandleWebhook_unexpectedException_throwsInternalServerError() {
        when(razorpayClientService.verifyWebhookSignature(anyString(), eq("good_sig"))).thenReturn(true);
        when(paymentRepository.findByRazorpayOrderId("order_err")).thenThrow(new RuntimeException("DB offline"));

        String payload = """
        {
          "event": "order.paid",
          "payload": {
            "payment": {
              "entity": {
                "id": "pay_err",
                "order_id": "order_err",
                "amount": 50000
              }
            }
          }
        }
        """;

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.handleWebhook(payload, "good_sig"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
    }

    @Test
    void testCancelMembershipWithRefund_nullRazorpayPaymentId_doesNotCallRefundPayment() {
        Membership membership = Membership.builder()
                .uuid("mem-no-rzp-pay-id")
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();

        Payment originalPayment = Payment.builder()
                .uuid("pay-no-rzp-id")
                .userUuid(userUuid)
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.SUCCESS)
                .amount(50000L)
                .razorpayPaymentId(null)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(membership));
        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(originalPayment));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING))
                .thenReturn(List.of());
        when(paymentRepository.findByPaymentUuidAndTransactionType("pay-no-rzp-id", TransactionType.REFUND))
                .thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipCancellationRefundResponse res = paymentService.cancelMembershipWithRefund(userEmail);
        assertNotNull(res);
        assertTrue(res.cancelled());
        assertNull(res.razorpayRefundId());
        verify(razorpayClientService, never()).refundPayment(any(), anyLong());
    }

    @Test
    void testProcessPermanentDeactivationRefund_zeroEligibleRefund_doesNotCallRefundPayment() {
        Payment originalPayment = Payment.builder()
                .uuid("pay-deact-zero-refund")
                .userUuid(userUuid)
                .amount(50000L)
                .razorpayPaymentId("pay_deact_zero")
                .build();

        when(paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid, PaymentType.MEMBERSHIP, TransactionType.PAYMENT, PaymentStatus.SUCCESS))
                .thenReturn(Optional.of(originalPayment));
        when(paymentRepository.findByPaymentUuidAndTransactionType("pay-deact-zero-refund", TransactionType.REFUND))
                .thenReturn(List.of());
        when(borrowRepository.countByUser_UuidAndReturnedDateIsNull(userUuid)).thenReturn(5L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponseDto res = paymentService.processPermanentDeactivationRefund(userUuid);
        assertNotNull(res);
        assertEquals(0L, res.amount());
        verify(razorpayClientService, never()).refundPayment(any(), anyLong());
    }

    @Test
    void testGetPaymentHistory_paginationEdgeCasesAndRefundInContent() {
        Payment paymentFailed = Payment.builder()
                .uuid("pay-failed")
                .userUuid(userUuid)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.FAILED)
                .amount(1000L)
                .build();

        Payment refundSuccess = Payment.builder()
                .uuid("rf-succ")
                .userUuid(userUuid)
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.SUCCESS)
                .amount(500L)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUserUuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(List.of(paymentFailed, refundSuccess));

        Page<Payment> pageResult = new PageImpl<>(List.of(refundSuccess), Pageable.unpaged(), 1);
        when(paymentRepository.findPaymentHistory(eq(userUuid), isNull(), isNull(), isNull(), any()))
                .thenReturn(pageResult);

        // Case 1: negative page, non-positive size, null search
        PaymentHistoryResponseDto res1 = paymentService.getPaymentHistory(userEmail, null, null, null, -1, 0);
        assertNotNull(res1);
        assertEquals(0L, res1.totalPaid());
        assertEquals(500L, res1.totalRefunded());
        assertEquals(0L, res1.netPaid());
        assertEquals(1, res1.content().size());

        // Case 2: search with blank string, size > 100
        PaymentHistoryResponseDto res2 = paymentService.getPaymentHistory(userEmail, null, null, "   ", 2, 150);
        assertNotNull(res2);
    }

    @Test
    void testDownloadInvoice_ownerNullNameAndNullEmail_fallsBackToUserString() throws Exception {
        User nullNameEmailUser = User.builder()
                .uuid("u-null-name-email")
                .name(null)
                .email(null)
                .role(RoleEnum.USER)
                .build();

        Payment payment = Payment.builder()
                .uuid("pay-null-owner-details")
                .userUuid("u-null-name-email")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(User.builder().uuid("admin-id").role(RoleEnum.ADMIN).build()));
        when(paymentRepository.findByUuid("pay-null-owner-details")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid("u-null-name-email")).thenReturn(Optional.of(nullNameEmailUser));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-FALLBACK");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-null-owner-details", "admin@example.com");
        assertNotNull(result);
        verify(s3Service).uploadFileBytes(any(), any(), eq("invoices/user/REC-FALLBACK.pdf"), any(), anyString());
    }

    @Test
    void testDownloadInvoice_ownerNameWithAtSymbol_substringBeforeAt() throws Exception {
        User atNameUser = User.builder()
                .uuid("u-at-name")
                .name("john.doe@domain.com")
                .email("other@domain.com")
                .role(RoleEnum.USER)
                .build();

        Payment payment = Payment.builder()
                .uuid("pay-at-name")
                .userUuid("u-at-name")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-at-name")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid("u-at-name")).thenReturn(Optional.of(atNameUser));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-AT-NAME");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-at-name", userEmail);
        assertNotNull(result);
        verify(s3Service).uploadFileBytes(any(), any(), eq("invoices/john_doe/REC-AT-NAME.pdf"), any(), anyString());
    }

    @Test
    void testDownloadInvoice_blankPdfUrl_generatesAndUploads() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-blank-pdf-url")
                .userUuid(userUuid)
                .pdfUrl("   ")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-blank-pdf-url")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-BLANK-URL");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-blank-pdf-url", userEmail);
        assertNotNull(result);
        verify(s3Service, never()).downloadFile(any(), any(), anyString());
        verify(s3Service).uploadFileBytes(any(), any(), anyString(), any(), anyString());
    }

    @Test
    void testDownloadInvoice_s3DownloadReturnsNull_generatesAndUploads() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-s3-null-bytes")
                .userUuid(userUuid)
                .pdfUrl("https://s3.example.com/null.pdf")
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("pay-s3-null-bytes")).thenReturn(Optional.of(payment));
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-S3-NULL");
        when(s3Service.downloadFile(any(), any(), anyString())).thenReturn(null);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("pay-s3-null-bytes", userEmail);
        assertNotNull(result);
        verify(s3Service).uploadFileBytes(any(), any(), anyString(), any(), anyString());
    }

    @Test
    void testFindPaymentByIdOrUuid_numericIdNotFoundInRepo_fallsBackToOrderId() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-num-fallback")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findById(9999L)).thenReturn(Optional.empty());
        when(paymentRepository.findByUuid("9999")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayPaymentId("9999")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("9999")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-9999");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("9999", userEmail);
        assertNotNull(result);
    }

    @Test
    void testFindPaymentByIdOrUuid_nonNumericFoundByOrderId() throws Exception {
        Payment payment = Payment.builder()
                .uuid("pay-order-lookup")
                .userUuid(userUuid)
                .status(PaymentStatus.SUCCESS)
                .transactionType(TransactionType.PAYMENT)
                .build();

        when(userProviderRepository.findByProviderId(userEmail)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(user));
        when(paymentRepository.findByUuid("order_by_order_id")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayPaymentId("order_by_order_id")).thenReturn(Optional.empty());
        when(paymentRepository.findByRazorpayOrderId("order_by_order_id")).thenReturn(Optional.of(payment));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(paymentRepository.findByPaymentUuidAndTransactionType(any(), any())).thenReturn(List.of());
        when(paymentDocumentGenerator.generateDocumentNumber(payment)).thenReturn("REC-ORDER-LK");
        when(paymentDocumentGenerator.generatePdf(any(), any(), anyList(), anyString())).thenReturn("PDF".getBytes());
        when(paymentRepository.save(payment)).thenReturn(payment);

        byte[] result = paymentService.downloadInvoice("order_by_order_id", userEmail);
        assertNotNull(result);
    }

    @Test
    void testFindPaymentByIdOrUuid_nullIdentifier_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.downloadInvoice(null, userEmail));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
