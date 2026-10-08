package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.enums.DonationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "book_donation")
public class BookDonation extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_uuid", referencedColumnName = "uuid", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "book_uuid", referencedColumnName = "uuid")
    private Book book;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String isbn;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "cover_image_key", length = 500)
    private String coverImageKey;

    @Column(name = "donated_book_count", nullable = false)
    private Integer donatedBookCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 36)
    @Builder.Default
    private DonationStatus status = DonationStatus.PENDING;

    @Column(name = "reviewed_by_uuid", length = 36)
    private String reviewedByUuid;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
}
