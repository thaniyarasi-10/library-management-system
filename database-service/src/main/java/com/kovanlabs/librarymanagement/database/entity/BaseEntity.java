package com.kovanlabs.librarymanagement.database.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Base abstract entity containing common primary key, legacy identifier,
 * and audit timestamp fields inherited by all entity classes.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(name = "uuid", unique = true, updatable = false, nullable = false, length = 36)
    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    @Column(name = "id", insertable = false, updatable = false, unique = true)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (Objects.isNull(uuid)) {
            uuid = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        if (Objects.isNull(createdAt)) {
            createdAt = now;
        }
        if (Objects.isNull(updatedAt)) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
