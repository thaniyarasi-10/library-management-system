package com.kovanlabs.librarymanagement.database.service;

import com.kovanlabs.librarymanagement.database.converter.EncryptionConverter;
import jakarta.persistence.AttributeConverter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DataEncryptionConvertorService implements AttributeConverter<String, String> {

    private final EncryptionConverter encryptionConverter;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return encryptionConverter.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptionConverter.decrypt(dbData);
    }
}
