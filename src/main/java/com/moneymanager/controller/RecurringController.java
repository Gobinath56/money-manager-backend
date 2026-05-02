package com.moneymanager.controller;

import com.moneymanager.dto.RecurringTransactionRequest;
import com.moneymanager.model.RecurringTransaction;
import com.moneymanager.service.RecurringTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recurring")
@RequiredArgsConstructor
public class RecurringController {

    private final RecurringTransactionService recurringService;

    @PostMapping
    public ResponseEntity<RecurringTransaction> create(
            @Valid @RequestBody RecurringTransactionRequest request) {
        return ResponseEntity.ok(recurringService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<RecurringTransaction>> getAll() {
        return ResponseEntity.ok(recurringService.getAllForCurrentUser());
    }

    @PatchMapping("/{id}/toggle")  // PATCH = partial update (toggle active flag)
    public ResponseEntity<RecurringTransaction> toggle(@PathVariable String id) {
        return ResponseEntity.ok(recurringService.toggle(id));
    }

    @PostMapping("/{id}/run")      // manual trigger from frontend button
    public ResponseEntity<Void> runNow(@PathVariable String id) {
        recurringService.runNow(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        recurringService.delete(id);
        return ResponseEntity.noContent().build();
    }
}