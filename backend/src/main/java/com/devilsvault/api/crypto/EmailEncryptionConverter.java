package com.devilsvault.api.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA converter that transparently encrypts / decrypts the email column
 * using AES-256-GCM.  Works identically on PostgreSQL and H2.
 *
 * <p>On PostgreSQL the pgcrypto extension is enabled for potential future
 * database-level batch operations, but runtime read/write goes through
 * this converter so that H2-based tests use the same code path.</p>
 */
@Component
@Converter
public class EmailEncryptionConverter implements AttributeConverter<String, byte[]> {

    private final EncryptionService encryptionService;

    public EmailEncryptionConverter(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptionService.decrypt(dbData);
    }
}
