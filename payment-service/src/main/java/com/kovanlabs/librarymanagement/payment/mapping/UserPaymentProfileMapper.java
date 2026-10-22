package com.kovanlabs.librarymanagement.payment.mapping;

import com.kovanlabs.librarymanagement.database.entity.UserPaymentProfile;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileRequestDto;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileResponseDto;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserPaymentProfileMapper {

    UserPaymentProfileMapper INSTANCE = Mappers.getMapper(UserPaymentProfileMapper.class);

    @Mapping(target = "maskedAccountNumber", source = "accountNumber", qualifiedByName = "maskAccountNumber")
    UserPaymentProfileResponseDto mapToResponse(UserPaymentProfile profile);

    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "userUuid", ignore = true)
    @Mapping(target = "cardLastFour", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserPaymentProfile mapToEntity(UserPaymentProfileRequestDto request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "userUuid", ignore = true)
    @Mapping(target = "cardLastFour", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(UserPaymentProfileRequestDto request, @MappingTarget UserPaymentProfile profile);

    @Named("maskAccountNumber")
    default String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        String clean = accountNumber.trim();
        if (clean.length() <= 4) {
            return clean;
        }
        int maskLength = clean.length() - 4;
        return "•".repeat(maskLength) + clean.substring(clean.length() - 4);
    }
}
