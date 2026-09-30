package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.enums.AuthProvider;
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
@Table(name = "user_provider")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserProvider extends BaseEntity {

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    @Builder.Default
    private AuthProvider provider = AuthProvider.AUTH0;

    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;
}
