package com.kovanlabs.librarymanagement.database.converter;

import com.kovanlabs.librarymanagement.database.enums.ReactivationRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ReactivationRequestStatusConverterTest {

    private ReactivationRequestStatusConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ReactivationRequestStatusConverter();
    }

    @Test
    @DisplayName("convertToDatabaseColumn with valid enum values should return enum name")
    void convertToDatabaseColumn_validValues() {
        assertEquals("PENDING", converter.convertToDatabaseColumn(ReactivationRequestStatus.PENDING));
        assertEquals("APPROVED", converter.convertToDatabaseColumn(ReactivationRequestStatus.APPROVED));
        assertEquals("REJECTED", converter.convertToDatabaseColumn(ReactivationRequestStatus.REJECTED));
    }

    @Test
    @DisplayName("convertToDatabaseColumn with null should return null")
    void convertToDatabaseColumn_null() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("convertToEntityAttribute with null, empty, or blank string should return null")
    void convertToEntityAttribute_nullOrBlank(String input) {
        assertNull(converter.convertToEntityAttribute(input));
    }

    @Test
    @DisplayName("convertToEntityAttribute with valid strings should return matching enum")
    void convertToEntityAttribute_validValues() {
        assertEquals(ReactivationRequestStatus.PENDING, converter.convertToEntityAttribute("PENDING"));
        assertEquals(ReactivationRequestStatus.PENDING, converter.convertToEntityAttribute("pending"));
        assertEquals(ReactivationRequestStatus.PENDING, converter.convertToEntityAttribute("  pending  "));
        assertEquals(ReactivationRequestStatus.APPROVED, converter.convertToEntityAttribute("APPROVED"));
        assertEquals(ReactivationRequestStatus.REJECTED, converter.convertToEntityAttribute("REJECTED"));
    }

    @Test
    @DisplayName("convertToEntityAttribute with invalid enum string should safely return null")
    void convertToEntityAttribute_invalidValue() {
        assertNull(converter.convertToEntityAttribute("UNKNOWN_STATUS"));
        assertNull(converter.convertToEntityAttribute("INVALID"));
    }
}
