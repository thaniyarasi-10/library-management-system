package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "book")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Book extends BaseEntity {

    private String title;
    
    private String author;
    
    private String isbn;

    private String coverImageUrl;

    private String coverImageKey;

    @Column(name = "book_count", nullable = false)
    @Builder.Default
    private Integer bookCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "salesforce_sync_status", nullable = false)
    @Builder.Default
    private SalesforceSyncStatus salesforceSyncStatus = SalesforceSyncStatus.PENDING;

    @Column(name = "salesforce_retry_count", nullable = false)
    @Builder.Default
    private int salesforceRetryCount = 0;
}
