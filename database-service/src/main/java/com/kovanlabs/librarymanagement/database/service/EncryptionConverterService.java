package com.kovanlabs.librarymanagement.database.service;

/**
 * Service contract for encrypting and decrypting sensitive database attributes using AES-GCM.
 */
public interface EncryptionConverterService {

     /**
      * Encrypts plaintext attribute string into Base64 ciphertext with IV.
      *
      * @param attribute Plaintext string to encrypt
      * @return Base64-encoded encrypted string containing IV prepended
      */
     String encrypt(String attribute);

     /**
      * Decrypts Base64 ciphertext string back into plaintext.
      *
      * @param dbData Base64 ciphertext from database
      * @return Decrypted plaintext string
      */
     String decrypt(String dbData);
}
