package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.converter.DataEncryptionConvertor;
import com.kovanlabs.librarymanagement.database.enums.MembershipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity

@Table(name = "membership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Membership extends BaseEntity {

    @Column(name = "membership_id", nullable = true, unique = true)
    private Long membershipId;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // using MySQL/MariaDB, BOOLEAN is essentially an alias for TINYINT(1)
    @Column(name = "is_signed", nullable = false)
    private boolean signed;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "signed_pdf_key")
    private String signedPdfKey;

    @Convert(converter = DataEncryptionConvertor.class)
    @Column(name = "signature_base64", columnDefinition = "LONGTEXT")
    private String signatureBase64;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}