package com.kovanlabs.librarymanagement.database.service;

public interface EncryptionConverterService {
     String encrypt(String attribute);
     String decrypt(String dbData);
}
