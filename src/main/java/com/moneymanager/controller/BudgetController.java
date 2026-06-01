package com.moneymanager.controller;

import com.moneymanager.dto.BudgetRequest;
import com.moneymanager.model.Budget;
import com.moneymanager.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FIX #18 — Budget limits now live in MongoDB, not localStorage.
 *
 * Endpoints:
 *   GET    /api/budgets              → all budgets for current user
 *   PUT    /api/budgets              → create or update (upsert by categoryName)
 *   DELETE /api/budgets/{id}         → delete by document ID
 *   DELETE /api/budgets/category/{name} → delete by category name (frontend convenience)
 */
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<List<Budget>> getAll() {
        return ResponseEntity.ok(budgetService.getAllForCurrentUser());
    }

    /**
     * PUT is used (not POST) because this is an upsert — the result is
     * idempotent. Calling it twice with the same body produces the same
     * stored state. PUT semantics match this perfectly.
     */
    @PutMapping
    public ResponseEntity<Budget> upsert(@Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.ok(budgetService.upsert(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        budgetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Convenience endpoint: delete by category name instead of document ID.
     * The frontend BudgetCard only knows the category name — it would need
     * a separate GET to find the ID before it could delete. This endpoint
     * skips that round trip.
     */
    @DeleteMapping("/category/{categoryName}")
    public ResponseEntity<Void> deleteByCategory(@PathVariable String categoryName) {
        budgetService.deleteByCategory(categoryName);
        return ResponseEntity.noContent().build();
    }
}