package com.kovanlabs.librarymanagement.database.converter;

import com.kovanlabs.librarymanagement.database.service.EncryptionConverterService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Converter
@RequiredArgsConstructor
public class DataEncryptionConvertor implements AttributeConverter<String, String> {

    private final EncryptionConverterService encryptionConverterService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return Objects.isNull(attribute) ? null : encryptionConverterService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return Objects.isNull(dbData) ? null : encryptionConverterService.decrypt(dbData);
    }
}