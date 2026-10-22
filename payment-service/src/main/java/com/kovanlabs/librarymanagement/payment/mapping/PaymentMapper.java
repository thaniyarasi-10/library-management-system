package com.kovanlabs.librarymanagement.payment.mapping;

import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface PaymentMapper {

    PaymentMapper INSTANCE = Mappers.getMapper(PaymentMapper.class);

    PaymentResponseDto mapToResponse(Payment payment);

    List<PaymentResponseDto> mapToResponseList(List<Payment> payments);
}
