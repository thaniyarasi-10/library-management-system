package com.kovanlabs.librarymanagement.fine.entity;

import com.kovanlabs.librarymanagement.fine.enums.FineStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fine")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pending_fine_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal pendingFineAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FineStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
