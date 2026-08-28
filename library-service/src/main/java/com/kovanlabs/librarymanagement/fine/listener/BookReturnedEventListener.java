package com.kovanlabs.librarymanagement.fine.listener;

import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.book.event.BookReturnedEvent;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.fine.service.FineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookReturnedEventListener {

    private final BorrowRepository borrowRepository;
    private final FineService fineService;

    @EventListener
    public void handleBookReturned(BookReturnedEvent event) {
        log.info("Received BookReturnedEvent for borrowId: {}", event.borrowId());

        Borrow borrow = borrowRepository.findById(event.borrowId())
                .orElseThrow(() -> new RuntimeException("Borrow not found with id: " + event.borrowId()));

        LocalDate dueDate = borrow.getDueDate();
        LocalDate returnedDate = borrow.getReturnedDate();

        if (returnedDate != null && dueDate != null && returnedDate.isAfter(dueDate)) {
            log.info("Book return is overdue (dueDate: {}, returnedDate: {}). Processing fine for borrowId: {}", dueDate, returnedDate, event.borrowId());
            fineService.processFineForBorrow(borrow);
        } else {
            log.info("Book return is on time or due date missing for borrowId: {}. No fine generated.", event.borrowId());
        }
    }
}
