package com.kovanlabs.librarymanagement.borrow.entity;

import com.kovanlabs.librarymanagement.borrow.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.user.entity.Users;
import jakarta.persistence.*;

import com.kovanlabs.librarymanagement.book.entity.Book;
import lombok.*;

import java.time.LocalDate;

@Builder
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "borrow")
public class Borrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "book_id")
    private Book book;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private Users users;

    private LocalDate borrowDate;

    private LocalDate dueDate;

    private LocalDate returnedDate;

    @Enumerated(EnumType.STRING)
    private BorrowStatus status;

}
