package com.kovanlabs.librarymanagement.payment.mapping;

import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.database.enums.TransactionType;
import com.kovanlabs.librarymanagement.payment.dto.PaymentHistoryResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper(imports = {UUID.class, LocalDateTime.class, TransactionType.class, List.class})
public interface PaymentMapper {

    PaymentMapper INSTANCE = Mappers.getMapper(PaymentMapper.class);

    @Mapping(target = "refunds", expression = "java(List.of())")
    PaymentResponseDto mapToResponse(Payment payment);

    PaymentResponseDto mapToResponse(Payment payment, List<PaymentResponseDto> refunds);

    List<PaymentResponseDto> mapToResponseList(List<Payment> payments);

    @Mapping(target = "uuid", expression = "java(UUID.randomUUID().toString())")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "transactionType", expression = "java(TransactionType.PAYMENT)")
    @Mapping(target = "razorpayRefundId", ignore = true)
    @Mapping(target = "paymentUuid", ignore = true)
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
    Payment createPaymentEntity(
            String userUuid,
            PaymentType paymentType,
            String razorpayOrderId,
            String razorpayPaymentId,
            String referenceId,
            Long amount,
            PaymentStatus status
    );

    @Mapping(target = "uuid", expression = "java(UUID.randomUUID().toString())")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userUuid", source = "original.userUuid")
    @Mapping(target = "transactionType", expression = "java(TransactionType.REFUND)")
    @Mapping(target = "paymentType", source = "original.paymentType")
    @Mapping(target = "razorpayOrderId", ignore = true)
    @Mapping(target = "razorpayPaymentId", ignore = true)
    @Mapping(target = "razorpayRefundId", source = "refundId")
    @Mapping(target = "paymentUuid", source = "original.uuid")
    @Mapping(target = "referenceId", source = "referenceId")
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())")
    Payment createRefundEntity(Payment original, String refundId, String referenceId, Long amount, PaymentStatus status);

    @Mapping(target = "currentPage", source = "page.number")
    @Mapping(target = "pageSize", source = "page.size")
    @Mapping(target = "totalElements", source = "page.totalElements")
    @Mapping(target = "totalPages", source = "page.totalPages")
    @Mapping(target = "isFirst", expression = "java(page.isFirst())")
    @Mapping(target = "isLast", expression = "java(page.isLast())")
    @Mapping(target = "hasNext", expression = "java(page.hasNext())")
    @Mapping(target = "hasPrevious", expression = "java(page.hasPrevious())")
    PaymentHistoryResponseDto mapToHistoryResponse(
            Long totalPaid,
            Long totalRefunded,
            Long netPaid,
            long successfulTransactionCount,
            List<PaymentResponseDto> content,
            Page<Payment> page
    );
}
