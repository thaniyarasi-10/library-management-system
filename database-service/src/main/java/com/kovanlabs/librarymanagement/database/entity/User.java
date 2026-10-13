package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.enums.AccountStatus;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity {

    @Column(nullable = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoleEnum role = RoleEnum.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "salesforce_sync_status", nullable = false)
    @Builder.Default
    private SalesforceSyncStatus salesforceSyncStatus = SalesforceSyncStatus.PENDING;

    @Column(name = "salesforce_retry_count", nullable = false)
    @Builder.Default
    private int salesforceRetryCount = 0;

    @Column(name = "phone", nullable = true)
    private String phone;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "inactivity_warning_sent_at")
    private LocalDateTime inactivityWarningSentAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "reactivation_requested", nullable = false)
    @Builder.Default
    private boolean reactivationRequested = false;

    @Column(name = "reactivation_requested_at")
    private LocalDateTime reactivationRequestedAt;
}
