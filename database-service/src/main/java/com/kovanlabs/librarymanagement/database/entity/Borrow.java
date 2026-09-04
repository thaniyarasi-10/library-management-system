package com.kovanlabs.librarymanagement.database.entity;

import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Builder
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "borrow")
public class Borrow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid", updatable = false, nullable = false, columnDefinition = "CHAR(36)")
    private UUID uuid;

    @Column(name = "id", insertable = false, updatable = false, unique = true)
    private Long id;

    @ManyToOne
    @JdbcTypeCode(SqlTypes.CHAR)
    @JoinColumn(name = "book_uuid", referencedColumnName = "uuid")
    private Book book;

    @ManyToOne
    @JdbcTypeCode(SqlTypes.CHAR)
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

}
