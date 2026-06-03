package com.moneymanager.controller;

import com.moneymanager.dto.DashboardResponse;
import com.moneymanager.dto.TransactionRequest;
import com.moneymanager.model.Transaction;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(
            @Valid @RequestBody TransactionRequest request) {
        return new ResponseEntity<>(
                transactionService.createTransaction(request), HttpStatus.CREATED);
    }

    // ── FIX #5: New paginated endpoint ────────────────────────────────────
    //
    // GET /api/transactions?page=0&size=25&sort=date,desc
    //
    // Query params:
    //   page  — zero-based page number (0 = first page)
    //   size  — records per page (default 25, max enforced in service)
    //   sort  — field,direction e.g. "date,desc" or "amount,asc"
    //
    // Returns a Page object containing:
    //   content          — the records for this page
    //   totalElements    — total number of records (for page count)
    //   totalPages       — total pages at this size
    //   number           — current page number
    //   size             — page size used
    //
    // The old GET /api/transactions (returns all) is kept below so the
    // dashboard aggregation still works without changes.
    @GetMapping("/paged")
    public ResponseEntity<Page<Transaction>> getPagedTransactions(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(
                transactionService.getPagedTransactions(page, size, sortBy, sortDir));
    }

    // ── Original endpoint — kept for dashboard data ────────────────────────
    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable String id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(
            @PathVariable String id,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable String id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter")
    public ResponseEntity<List<Transaction>> getFilteredTransactions(
            @RequestParam(required = false) Division division,
            @RequestParam(required = false) String category,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(
                transactionService.getFilteredTransactions(division, category, startDate, endDate));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboardData() {
        return ResponseEntity.ok(transactionService.getDashboardData());
    }
}