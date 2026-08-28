package com.kovanlabs.librarymanagement.database.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "book")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", updatable = false, nullable = false, columnDefinition = "CHAR(36)")
    private UUID uuid;

    @Column(name = "id", insertable = false, updatable = false, unique = true)
    private Long id;

    private String title;
    
    private String author;
    
    private String isbn;

    private String coverImageUrl;

    private String coverImageKey;

}
