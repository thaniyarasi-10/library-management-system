package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.database.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByUuid(String uuid);

    Optional<Payment> findByRazorpayOrderId(String orderId);

    Optional<Payment> findByRazorpayPaymentId(String paymentId);

    Optional<Payment> findByRazorpayRefundId(String refundId);

    List<Payment> findByUserUuidOrderByCreatedAtDesc(String userUuid);

    List<Payment> findByPaymentUuidAndTransactionType(String paymentUuid, TransactionType transactionType);

    Optional<Payment> findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
            String userUuid, PaymentType paymentType, TransactionType transactionType, PaymentStatus status);

    boolean existsByRazorpayPaymentId(String paymentId);

    boolean existsByRazorpayRefundId(String refundId);

    boolean existsByPaymentUuidAndTransactionType(
            String paymentUuid, TransactionType transactionType);

    List<Payment> findByUserUuidAndTransactionType(String userUuid, TransactionType transactionType);
}
