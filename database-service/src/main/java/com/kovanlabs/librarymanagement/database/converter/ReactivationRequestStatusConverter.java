package com.kovanlabs.librarymanagement.database.converter;

import com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Objects;

/**
 * JPA AttributeConverter for safely converting {@link ReactivationRequestStatus} enum
 * to/from the database column. Safely handles null, empty, or whitespace strings by
 * converting them to null, preventing unexpected IllegalArgumentException upon entity hydration.
 */
@Converter(autoApply = true)
public class ReactivationRequestStatusConverter implements AttributeConverter<ReactivationRequestStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReactivationRequestStatus attribute) {
        return Objects.nonNull(attribute) ? attribute.name() : null;
    }

    @Override
    public ReactivationRequestStatus convertToEntityAttribute(String dbData) {
        if (Objects.isNull(dbData) || dbData.isBlank()) {
            return null;
        }
        try {
            return ReactivationRequestStatus.valueOf(dbData.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
