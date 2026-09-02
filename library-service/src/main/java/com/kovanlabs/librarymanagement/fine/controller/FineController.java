package com.kovanlabs.librarymanagement.fine.controller;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.fine.dto.FineResponseDto;
import com.kovanlabs.librarymanagement.fine.service.FineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/fines")
@RequiredArgsConstructor
public class FineController {

    private final FineService fineService;

    @GetMapping
    public ResponseEntity<List<FineResponseDto>> getAllFines() {
        return ResponseEntity.ok(fineService.getAllFinesDto());
    }

    @GetMapping("/me")
    public ResponseEntity<List<FineResponseDto>> getMyFines(Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(fineService.getFinesDtoByUserEmail(principal.getName()));
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<FineResponseDto> payFine(@PathVariable("id") Long id) {
        Fine fine = fineService.payFine(id);
        return ResponseEntity.ok(fineService.mapToDtoWithDetails(fine));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<FineResponseDto>> getFinesByUserId(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(fineService.getFinesDtoByUserId(userId));
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

