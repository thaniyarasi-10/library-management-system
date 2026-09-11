package com.kovanlabs.librarymanagement.database.service;

import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Component
@Converter
public interface EncryptionConverterService {
     String encrypt(String attribute);
     String decrypt(String dbData);
}
