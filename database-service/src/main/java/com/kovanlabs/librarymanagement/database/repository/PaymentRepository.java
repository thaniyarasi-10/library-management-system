package com.kovanlabs.librarymanagement.database.repository;

import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.database.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findById(Long id);

    Optional<Payment> findByUuid(String uuid);

    Optional<Payment> findByRazorpayOrderId(String orderId);

    Optional<Payment> findByRazorpayPaymentId(String paymentId);

    Optional<Payment> findByRazorpayRefundId(String refundId);

    List<Payment> findByUserUuidOrderByCreatedAtDesc(String userUuid);

    List<Payment> findByPaymentUuidAndTransactionType(String paymentUuid, TransactionType transactionType);

    Optional<Payment> findTopByUserUuidAndPaymentTypeAndTransactionTypeAndStatusOrderByCreatedAtDesc(
            String userUuid, PaymentType paymentType, TransactionType transactionType, PaymentStatus status);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.userUuid = :userUuid
          AND (:paymentType IS NULL OR p.paymentType = :paymentType)
          AND (:status IS NULL OR p.status = :status)
          AND (:search IS NULL OR :search = '' OR
               LOWER(p.razorpayOrderId) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(p.razorpayPaymentId) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(p.razorpayRefundId) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(p.referenceId) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Page<Payment> findPaymentHistory(
            @Param("userUuid") String userUuid,
            @Param("paymentType") PaymentType paymentType,
            @Param("status") PaymentStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
