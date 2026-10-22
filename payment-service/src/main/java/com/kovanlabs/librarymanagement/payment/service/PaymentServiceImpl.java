package com.kovanlabs.librarymanagement.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.*;
import com.kovanlabs.librarymanagement.database.repository.*;
import com.kovanlabs.librarymanagement.payment.config.RazorpayProperties;
import com.kovanlabs.librarymanagement.payment.dto.MembershipCancellationRefundResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentHistoryResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.PaymentOrderResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.RazorpayOrderDetails;
import com.kovanlabs.librarymanagement.payment.dto.VerifyPaymentRequest;
import com.kovanlabs.librarymanagement.payment.mapping.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    public static final long MEMBERSHIP_FEE_PAISE = 50000L; // ₹500 in paise
    public static final long BOOK_REPLACEMENT_FEE_PAISE = 10000L; // ₹100 per unreturned book

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final MembershipRepository membershipRepository;
    private final FineRepository fineRepository;
    private final BorrowRepository borrowRepository;
    private final RazorpayClientService razorpayClientService;
    private final RazorpayProperties razorpayProperties;
    private final ObjectMapper objectMapper;

    private User findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User identity is required");
        }
        return userProviderRepository.findByProviderId(identifier)
                .flatMap(up -> userRepository.findByUuid(up.getUserUuid()))
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + identifier));
    }

    @Override
    @Transactional
    public PaymentOrderResponse createMembershipOrder(String email) {
        User user = findUserByIdentifier(email);

        boolean hasActive = membershipRepository.findTopByUserUuidOrderByCreatedAtDesc(user.getUuid())
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE
                        && (Objects.isNull(m.getExpiryDate()) || !m.getExpiryDate().isBefore(LocalDate.now())))
                .orElse(false);

        if (hasActive) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already has an active library membership");
        }

        Membership membership = membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(
                user.getUuid(),
                List.of(MembershipStatus.PENDING))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No membership application found. Please apply first."));

        if (!membership.isSigned()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Membership agreement must be digitally signed before making payment");
        }

        String receipt = "mem_" + membership.getUuid();
        String orderId = razorpayClientService.createOrder(
                MEMBERSHIP_FEE_PAISE,
                receipt,
                Map.of(
                        "user_uuid", user.getUuid(),
                        "payment_type", PaymentType.MEMBERSHIP.name(),
                        "reference_id", membership.getUuid()
                )
        );

        log.info("Created membership payment order: {} for user: {}", orderId, email);

        return new PaymentOrderResponse(
                orderId,
                MEMBERSHIP_FEE_PAISE,
                razorpayProperties.getKeyId(),
                PaymentType.MEMBERSHIP.name(),
                membership.getUuid()
        );
    }

    @Override
    @Transactional
    public PaymentOrderResponse createFineOrder(Long fineId, String email) {
        User user = findUserByIdentifier(email);

        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fine record not found with id: " + fineId));

        if (user.getRole() != RoleEnum.ADMIN && !fine.getUserUuid().equals(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: Fine does not belong to you");
        }

        if (fine.getStatus() == FineStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fine is already paid");
        }

        BigDecimal pendingAmount = fine.getPendingFineAmount();
        if (Objects.isNull(pendingAmount) || pendingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pending fine amount must be greater than zero");
        }

        long amountInPaise = pendingAmount.multiply(BigDecimal.valueOf(100)).longValue();
        String receipt = "fine_" + fineId + "_" + System.currentTimeMillis();

        String orderId = razorpayClientService.createOrder(
                amountInPaise,
                receipt,
                Map.of(
                        "user_uuid", fine.getUserUuid(),
                        "payment_type", PaymentType.FINE.name(),
                        "fine_id", fineId.toString(),
                        "reference_id", fineId.toString()
                )
        );

        log.info("Created fine payment order: {} for fineId: {} amount: {} paise", orderId, fineId, amountInPaise);

        return new PaymentOrderResponse(
                orderId,
                amountInPaise,
                razorpayProperties.getKeyId(),
                PaymentType.FINE.name(),
                fineId.toString()
        );
    }

    @Override
    @Transactional
    public PaymentResponseDto verifyPayment(VerifyPaymentRequest request, String email) {
        User user = findUserByIdentifier(email);

        Optional<Payment> existingPaymentOpt = paymentRepository.findByRazorpayOrderId(request.razorpayOrderId());
        if (existingPaymentOpt.isPresent()) {
            Payment existing = existingPaymentOpt.get();
            if (user.getRole() != RoleEnum.ADMIN && !existing.getUserUuid().equals(user.getUuid())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access Denied: Payment transaction does not belong to you");
            }
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Payment order {} already successfully verified", request.razorpayOrderId());
                return PaymentMapper.INSTANCE.mapToResponse(existing);
            }
        }

        boolean isValid = razorpayClientService.verifyPaymentSignature(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature()
        );

        RazorpayOrderDetails orderDetails = razorpayClientService.fetchOrder(request.razorpayOrderId());
        Map<String, String> notes = orderDetails.notes() != null ? orderDetails.notes() : Map.of();
        String orderUserUuid = notes.getOrDefault("user_uuid", user.getUuid());
        String paymentTypeStr = notes.getOrDefault("payment_type", PaymentType.MEMBERSHIP.name());
        PaymentType paymentType = PaymentType.valueOf(paymentTypeStr);
        String referenceId = notes.getOrDefault("reference_id", notes.get("fine_id"));
        long amount = orderDetails.amount() > 0 ? orderDetails.amount() : (paymentType == PaymentType.MEMBERSHIP ? MEMBERSHIP_FEE_PAISE : 0L);

        if (user.getRole() != RoleEnum.ADMIN && !orderUserUuid.equals(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access Denied: Payment transaction does not belong to you");
        }

        if (!isValid) {
            log.warn("Payment signature verification failed for order: {}", request.razorpayOrderId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment signature verification");
        }

        Payment payment = existingPaymentOpt.orElseGet(() ->
                PaymentMapper.INSTANCE.createPaymentEntity(
                        orderUserUuid,
                        paymentType,
                        request.razorpayOrderId(),
                        request.razorpayPaymentId(),
                        referenceId,
                        amount,
                        PaymentStatus.SUCCESS
                )
        );

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId(request.razorpayPaymentId());
        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment verified successfully for order: {}, paymentId: {}", request.razorpayOrderId(), request.razorpayPaymentId());

        if (payment.getPaymentType() == PaymentType.MEMBERSHIP) {
            activateMembershipInternal(payment.getUserUuid());
        } else if (payment.getPaymentType() == PaymentType.FINE) {
            settleFineInternal(payment.getReferenceId());
        }

        return PaymentMapper.INSTANCE.mapToResponse(savedPayment);
    }

    @Override
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Missing X-Razorpay-Signature header in webhook request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-Razorpay-Signature header");
        }
        if (payload == null || payload.isBlank()) {
            log.warn("Missing webhook payload body");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing webhook payload body");
        }

        boolean isValid = razorpayClientService.verifyWebhookSignature(payload, signatureHeader);
        if (!isValid) {
            log.warn("Invalid webhook signature received from Razorpay");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Failed to parse Razorpay webhook payload: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed JSON webhook payload");
        }

        String event = root.path("event").asText("");
        log.info("Received verified Razorpay webhook event: {}", event);

        try {
            if ("payment.captured".equalsIgnoreCase(event) || "order.paid".equalsIgnoreCase(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String orderId = paymentEntity.path("order_id").asText(null);
                String paymentId = paymentEntity.path("id").asText(null);
                long amount = paymentEntity.path("amount").asLong(0L);

                if (Objects.nonNull(orderId) && !orderId.isBlank()) {
                    Optional<Payment> existingOpt = paymentRepository.findByRazorpayOrderId(orderId);
                    if (existingOpt.isPresent()) {
                        Payment payment = existingOpt.get();
                        if (payment.getStatus() == PaymentStatus.SUCCESS) {
                            log.info("Webhook duplicate event: payment order {} is already SUCCESS. Skipping.", orderId);
                            return;
                        }

                        payment.setStatus(PaymentStatus.SUCCESS);
                        if (Objects.nonNull(paymentId) && !paymentId.isBlank()) {
                            payment.setRazorpayPaymentId(paymentId);
                        }
                        paymentRepository.save(payment);
                        log.info("Webhook confirmed payment order {} as SUCCESS (paymentId: {})", orderId, paymentId);

                        if (payment.getPaymentType() == PaymentType.MEMBERSHIP) {
                            activateMembershipInternal(payment.getUserUuid());
                        } else if (payment.getPaymentType() == PaymentType.FINE) {
                            settleFineInternal(payment.getReferenceId());
                        }
                    } else {
                        JsonNode notesNode = paymentEntity.path("notes");
                        String userUuid = notesNode.path("user_uuid").asText(null);
                        String paymentTypeStr = notesNode.path("payment_type").asText(null);
                        String referenceId = notesNode.path("reference_id").asText(notesNode.path("fine_id").asText(null));

                        if (userUuid == null || paymentTypeStr == null) {
                            try {
                                RazorpayOrderDetails details = razorpayClientService.fetchOrder(orderId);
                                if (userUuid == null) userUuid = details.notes().get("user_uuid");
                                if (paymentTypeStr == null) paymentTypeStr = details.notes().get("payment_type");
                                if (referenceId == null) referenceId = details.notes().getOrDefault("reference_id", details.notes().get("fine_id"));
                                if (amount == 0L) amount = details.amount();
                            } catch (Exception e) {
                                log.warn("Unable to fetch order notes in webhook: {}", e.getMessage());
                            }
                        }

                        if (userUuid != null && paymentTypeStr != null) {
                            Payment payment = PaymentMapper.INSTANCE.createPaymentEntity(
                                    userUuid,
                                    PaymentType.valueOf(paymentTypeStr),
                                    orderId,
                                    paymentId,
                                    referenceId,
                                    amount,
                                    PaymentStatus.SUCCESS
                            );
                            Payment saved = paymentRepository.save(payment);
                            log.info("Webhook created and confirmed payment order {} as SUCCESS (paymentId: {})", orderId, paymentId);

                            if (saved.getPaymentType() == PaymentType.MEMBERSHIP) {
                                activateMembershipInternal(saved.getUserUuid());
                            } else if (saved.getPaymentType() == PaymentType.FINE) {
                                settleFineInternal(saved.getReferenceId());
                            }
                        }
                    }
                }
            } else if ("payment.failed".equalsIgnoreCase(event)) {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                String orderId = paymentEntity.path("order_id").asText(null);
                String paymentId = paymentEntity.path("id").asText(null);
                long amount = paymentEntity.path("amount").asLong(0L);

                if (Objects.nonNull(orderId) && !orderId.isBlank()) {
                    Optional<Payment> existingOpt = paymentRepository.findByRazorpayOrderId(orderId);
                    if (existingOpt.isPresent()) {
                        Payment payment = existingOpt.get();
                        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.SUCCESS) {
                            return;
                        }
                        payment.setStatus(PaymentStatus.FAILED);
                        if (Objects.nonNull(paymentId) && !paymentId.isBlank()) {
                            payment.setRazorpayPaymentId(paymentId);
                        }
                        paymentRepository.save(payment);
                        log.info("Webhook marked payment order {} as FAILED", orderId);
                    } else {
                        JsonNode notesNode = paymentEntity.path("notes");
                        String userUuid = notesNode.path("user_uuid").asText(null);
                        String paymentTypeStr = notesNode.path("payment_type").asText(null);
                        String referenceId = notesNode.path("reference_id").asText(notesNode.path("fine_id").asText(null));

                        if (userUuid != null && paymentTypeStr != null) {
                            Payment failedPayment = PaymentMapper.INSTANCE.createPaymentEntity(
                                    userUuid,
                                    PaymentType.valueOf(paymentTypeStr),
                                    orderId,
                                    paymentId,
                                    referenceId,
                                    amount,
                                    PaymentStatus.FAILED
                            );
                            paymentRepository.save(failedPayment);
                            log.info("Webhook created failed payment order {}", orderId);
                        }
                    }
                }
            } else if ("refund.processed".equalsIgnoreCase(event)) {
                JsonNode refundEntity = root.path("payload").path("refund").path("entity");
                String refundId = refundEntity.path("id").asText(null);
                String paymentId = refundEntity.path("payment_id").asText(null);
                long refundAmount = refundEntity.path("amount").asLong(0L);

                if (Objects.nonNull(refundId) && !refundId.isBlank()) {
                    Payment refundPayment = paymentRepository.findByRazorpayRefundId(refundId)
                            .orElseGet(() -> {
                                if (Objects.nonNull(paymentId) && !paymentId.isBlank()) {
                                    return paymentRepository.findByRazorpayPaymentId(paymentId)
                                            .map(orig -> PaymentMapper.INSTANCE.createRefundEntity(
                                                    orig,
                                                    refundId,
                                                    paymentId,
                                                    refundAmount > 0 ? refundAmount : orig.getAmount(),
                                                    PaymentStatus.SUCCESS
                                            ))
                                            .map(paymentRepository::save)
                                            .orElse(null);
                                }
                                return null;
                            });

                    if (Objects.nonNull(refundPayment)) {
                        if (refundPayment.getStatus() != PaymentStatus.SUCCESS) {
                            refundPayment.setStatus(PaymentStatus.SUCCESS);
                            paymentRepository.save(refundPayment);
                            log.info("Webhook marked refund {} as SUCCESS", refundId);
                        }
                    }
                }
            } else if ("refund.failed".equalsIgnoreCase(event)) {
                JsonNode refundEntity = root.path("payload").path("refund").path("entity");
                String refundId = refundEntity.path("id").asText(null);
                if (Objects.nonNull(refundId) && !refundId.isBlank()) {
                    paymentRepository.findByRazorpayRefundId(refundId).ifPresent(refundPayment -> {
                        if (refundPayment.getStatus() == PaymentStatus.FAILED) {
                            return;
                        }
                        refundPayment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(refundPayment);
                        log.info("Webhook marked refund {} as FAILED", refundId);
                    });
                }
            }
        } catch (Exception e) {
            log.error("Error processing Razorpay webhook event [{}]: {}", event, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing webhook: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public MembershipCancellationRefundResponse cancelMembershipWithRefund(String email) {
        User user = findUserByIdentifier(email);

        Membership membership = membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(
                user.getUuid(),
                List.of(MembershipStatus.ACTIVE, MembershipStatus.PENDING))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "User does not have an active or pending membership to cancel"));

        Payment originalPayment = paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                user.getUuid(),
                PaymentType.MEMBERSHIP,
                TransactionType.PAYMENT,
                PaymentStatus.SUCCESS
        ).orElse(null);

        BigDecimal unpaidFines = fineRepository.findByUserUuidAndStatus(user.getUuid(), FineStatus.PENDING).stream()
                .map(Fine::getPendingFineAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long unpaidFinesPaise = (Objects.nonNull(unpaidFines))
                ? unpaidFines.multiply(BigDecimal.valueOf(100)).longValue()
                : 0L;

        long originalDepositPaise = MEMBERSHIP_FEE_PAISE;
        long eligibleRefundPaise = Math.max(0L, originalDepositPaise - unpaidFinesPaise);
        String refundId = null;

        if (Objects.nonNull(originalPayment)) {
            long alreadyRefunded = paymentRepository.findByPaymentUuidAndTransactionType(
                    originalPayment.getUuid(),
                    TransactionType.REFUND
            ).stream()
                    .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                    .mapToLong(Payment::getAmount)
                    .sum();

            long maxRefundable = Math.max(0L, originalPayment.getAmount() - alreadyRefunded);
            long refundToIssue = Math.min(eligibleRefundPaise, maxRefundable);

            if (refundToIssue > 0 && Objects.nonNull(originalPayment.getRazorpayPaymentId())) {
                refundId = razorpayClientService.refundPayment(originalPayment.getRazorpayPaymentId(), refundToIssue);
            }

            Payment refundRecord = PaymentMapper.INSTANCE.createRefundEntity(
                    originalPayment,
                    refundId,
                    originalPayment.getRazorpayPaymentId(),
                    refundToIssue,
                    PaymentStatus.SUCCESS
            );

            paymentRepository.save(refundRecord);
            eligibleRefundPaise = refundToIssue;
        }

        membership.setStatus(MembershipStatus.CANCELLED);
        membership.setCancelledAt(LocalDateTime.now());
        membershipRepository.save(membership);
        log.info("Membership cancelled with refund for user: {}, refund amount: {} paise", email, eligibleRefundPaise);

        return new MembershipCancellationRefundResponse(
                true,
                originalDepositPaise,
                unpaidFinesPaise,
                eligibleRefundPaise,
                refundId,
                "Membership cancelled successfully. Refund processed."
        );
    }

    @Override
    @Transactional
    public PaymentResponseDto processPermanentDeactivationRefund(String userUuid) {
        log.info("Evaluating permanent deactivation refund for userUuid: {}", userUuid);

        Payment originalPayment = paymentRepository.findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
                userUuid,
                PaymentType.MEMBERSHIP,
                TransactionType.PAYMENT,
                PaymentStatus.SUCCESS
        ).orElse(null);

        if (Objects.isNull(originalPayment) || Objects.isNull(originalPayment.getRazorpayPaymentId())) {
            log.info("No successful membership payment found for permanently deactivated userUuid: {}", userUuid);
            return null;
        }

        long alreadyRefunded = paymentRepository.findByPaymentUuidAndTransactionType(
                originalPayment.getUuid(),
                TransactionType.REFUND
        ).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToLong(Payment::getAmount)
                .sum();

        long maxRefundable = Math.max(0L, originalPayment.getAmount() - alreadyRefunded);
        if (maxRefundable <= 0) {
            log.info("Original payment {} already fully refunded", originalPayment.getUuid());
            return null;
        }

        long unreturnedBooks = borrowRepository.countByUser_UuidAndReturnedDateIsNull(userUuid);
        long deductionPaise = unreturnedBooks * BOOK_REPLACEMENT_FEE_PAISE;

        long eligibleRefundPaise = Math.max(0L, MEMBERSHIP_FEE_PAISE - deductionPaise);
        long refundToIssue = Math.min(eligibleRefundPaise, maxRefundable);

        String refundId = null;
        if (refundToIssue > 0) {
            refundId = razorpayClientService.refundPayment(originalPayment.getRazorpayPaymentId(), refundToIssue);
        }

        Payment refundRecord = PaymentMapper.INSTANCE.createRefundEntity(
                originalPayment,
                refundId,
                originalPayment.getRazorpayPaymentId(),
                refundToIssue,
                PaymentStatus.SUCCESS
        );

        Payment saved = paymentRepository.save(refundRecord);

        return PaymentMapper.INSTANCE.mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponseDto> getUserPayments(String email) {
        User user = findUserByIdentifier(email);
        List<Payment> list = paymentRepository.findByUserUuidOrderByCreatedAtDesc(user.getUuid());
        return PaymentMapper.INSTANCE.mapToResponseList(list);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentHistoryResponseDto getPaymentHistory(
            String userIdentifier,
            PaymentType paymentType,
            PaymentStatus status,
            String search,
            int page,
            int size
    ) {
        User user = findUserByIdentifier(userIdentifier);

        List<Payment> allUserPayments = paymentRepository.findByUserUuidOrderByCreatedAtDesc(user.getUuid());

        long totalPaid = allUserPayments.stream()
                .filter(p -> p.getTransactionType() == TransactionType.PAYMENT
                        && p.getStatus() == PaymentStatus.SUCCESS)
                .mapToLong(Payment::getAmount)
                .sum();

        long totalRefunded = allUserPayments.stream()
                .filter(p -> p.getTransactionType() == TransactionType.REFUND
                        && p.getStatus() == PaymentStatus.SUCCESS)
                .mapToLong(Payment::getAmount)
                .sum();

        long netPaid = Math.max(0L, totalPaid - totalRefunded);

        long successfulTransactionCount = allUserPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .count();

        int pageIndex = Math.max(0, page);
        int pageSize = (size <= 0) ? 10 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String sanitizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<Payment> pageResult = paymentRepository.findPaymentHistory(
                user.getUuid(),
                paymentType,
                status,
                sanitizedSearch,
                pageable
        );

        List<PaymentResponseDto> content = pageResult.getContent().stream()
                .map(p -> {
                    if (p.getTransactionType() == TransactionType.PAYMENT) {
                        List<Payment> associatedRefunds = paymentRepository.findByPaymentUuidAndTransactionType(
                                p.getUuid(),
                                TransactionType.REFUND
                        );
                        List<PaymentResponseDto> refundDtos = PaymentMapper.INSTANCE.mapToResponseList(associatedRefunds);
                        return PaymentMapper.INSTANCE.mapToResponse(p, refundDtos);
                    } else {
                        return PaymentMapper.INSTANCE.mapToResponse(p);
                    }
                })
                .toList();

        return PaymentMapper.INSTANCE.mapToHistoryResponse(
                totalPaid,
                totalRefunded,
                netPaid,
                successfulTransactionCount,
                content,
                pageResult
        );
    }


    private void activateMembershipInternal(String userUuid) {
        membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(
                userUuid,
                List.of(MembershipStatus.PENDING, MembershipStatus.ACTIVE)
        ).ifPresent(membership -> {
            if (membership.getStatus() != MembershipStatus.ACTIVE) {
                membership.setStatus(MembershipStatus.ACTIVE);
                membership.setActivatedAt(LocalDateTime.now());
                membership.setExpiryDate(LocalDate.now().plusYears(1));
                membershipRepository.save(membership);
                log.info("Activated library membership for user: {}", userUuid);
            }
        });
    }

    private void settleFineInternal(String referenceId) {
        try {
            Long fineId = Long.parseLong(referenceId);
            fineRepository.findById(fineId).ifPresent(fine -> {
                fine.setStatus(FineStatus.PAID);
                fine.setPendingFineAmount(BigDecimal.ZERO);
                fineRepository.save(fine);
                log.info("Settled fineId: {} as PAID after payment", fineId);
            });
        } catch (Exception e) {
            log.error("Failed to mark fine as paid after payment: {}", e.getMessage(), e);
        }
    }
}
