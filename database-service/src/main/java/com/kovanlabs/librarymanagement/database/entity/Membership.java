package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.enums.MembershipStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "membership")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", updatable = false, nullable = false, columnDefinition = "CHAR(36)")
    private UUID uuid;

    @Column(name = "membership_id", nullable = false, unique = true)
    private Long membershipId;

    @Column(name = "user_uuid", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID userUuid;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}