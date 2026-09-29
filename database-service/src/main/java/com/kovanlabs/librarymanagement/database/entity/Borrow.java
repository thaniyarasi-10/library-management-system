package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
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

import java.time.LocalDate;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "borrow")
public class Borrow extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "book_uuid", referencedColumnName = "uuid")
    private Book book;

    @ManyToOne
    @JoinColumn(name = "user_uuid", referencedColumnName = "uuid")
    private User user;

    private LocalDate borrowDate;

    private LocalDate dueDate;

    private LocalDate returnedDate;

    @Enumerated(EnumType.STRING)
    private BorrowStatus status;

    @Column(name = "reward_processed", nullable = false)
    @Builder.Default
    private boolean rewardProcessed = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "salesforce_sync_status", nullable = false)
    @Builder.Default
    private SalesforceSyncStatus salesforceSyncStatus = SalesforceSyncStatus.PENDING;

    @Column(name = "salesforce_retry_count", nullable = false)
    @Builder.Default
    private int salesforceRetryCount = 0;
}
