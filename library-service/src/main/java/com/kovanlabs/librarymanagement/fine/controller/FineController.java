package com.kovanlabs.librarymanagement.fine.controller;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.fine.service.FineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/fines")
@RequiredArgsConstructor
public class FineController {

    private final FineService fineService;

    @PostMapping("/{id}/pay")
    public ResponseEntity<Fine> payFine(@PathVariable("id") Long id) {
        Fine fine = fineService.payFine(id);
        return ResponseEntity.ok(fine);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Fine>> getFinesByUserId(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(fineService.getFinesByUserId(userId));
    }

    @GetMapping("/user/{userId}/pending")
    public ResponseEntity<List<Fine>> getPendingFinesByUserId(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(fineService.getPendingFinesByUserId(userId));
    }

    @GetMapping("/user/{userId}/pending-total")
    public ResponseEntity<BigDecimal> getUserTotalPendingFine(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(fineService.calculateTotalPendingFineForUser(userId));
    }
}
