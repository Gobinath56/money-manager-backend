package com.moneymanager.controller;

import com.moneymanager.dto.UserCategoryRequest;
import com.moneymanager.model.Transaction.TransactionType;
import com.moneymanager.model.UserCategory;
import com.moneymanager.service.UserCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class UserCategoryController {

    private final UserCategoryService categoryService;

    // Get all categories for current user
    @GetMapping
    public ResponseEntity<List<UserCategory>> getAll() {
        return ResponseEntity.ok(categoryService.getAllForCurrentUser());
    }

    // Get by type — ?type=EXPENSE or ?type=INCOME
    @GetMapping("/by-type")
    public ResponseEntity<List<UserCategory>> getByType(
            @RequestParam TransactionType type) {
        return ResponseEntity.ok(categoryService.getByType(type));
    }

    // Create a new custom category
    @PostMapping
    public ResponseEntity<UserCategory> create(
            @Valid @RequestBody UserCategoryRequest request) {
        return ResponseEntity.ok(categoryService.create(request));
    }

    // Add a subcategory to an existing category
    @PostMapping("/{id}/subcategories")
    public ResponseEntity<UserCategory> addSubCategory(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
                categoryService.addSubCategory(id, body.get("name"))
        );
    }

    // Remove a subcategory
    @DeleteMapping("/{id}/subcategories/{subName}")
    public ResponseEntity<UserCategory> removeSubCategory(
            @PathVariable String id,
            @PathVariable String subName) {
        return ResponseEntity.ok(
                categoryService.removeSubCategory(id, subName)
        );
    }

    // Delete a custom category
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}