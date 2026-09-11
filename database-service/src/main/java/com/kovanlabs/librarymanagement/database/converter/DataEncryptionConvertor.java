package com.kovanlabs.librarymanagement.database.converter;

import com.kovanlabs.librarymanagement.database.service.EncryptionConverterService;
import jakarta.persistence.AttributeConverter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DataEncryptionConvertor implements AttributeConverter<String, String> {

    private final EncryptionConverterService encryptionConverterService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return encryptionConverterService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptionConverterService.decrypt(dbData);
    }
}
