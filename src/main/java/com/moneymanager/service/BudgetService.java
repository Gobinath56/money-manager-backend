package com.moneymanager.service;

import com.moneymanager.dto.BudgetRequest;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.Budget;
import com.moneymanager.repository.BudgetRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final SecurityUtils securityUtils;

    // ═══════════════════════════════════════════
    //  GET ALL  —  scoped to current user
    // ═══════════════════════════════════════════

    public List<Budget> getAllForCurrentUser() {
        return budgetRepository.findByUserId(securityUtils.getCurrentUserEmail());
    }

    // ═══════════════════════════════════════════
    //  UPSERT  —  create or update in one call
    //
    //  Why upsert instead of separate create/update?
    //  The frontend "set limit" button should always work regardless of whether
    //  a budget already exists for that category. Upsert eliminates the need
    //  for the frontend to track whether it should POST or PUT.
    // ═══════════════════════════════════════════

    public Budget upsert(BudgetRequest request) {
        String userId = securityUtils.getCurrentUserEmail();
        String categoryName = request.getCategoryName().toUpperCase().trim();

        // Try to find an existing budget for this category
        Budget budget = budgetRepository
                .findByUserIdAndCategoryName(userId, categoryName)
                .orElse(null);

        LocalDateTime now = LocalDateTime.now();

        if (budget == null) {
            // Create new
            budget = new Budget();
            budget.setUserId(userId);
            budget.setCategoryName(categoryName);
            budget.setCreatedAt(now);
        }

        // Update fields (works for both new and existing)
        budget.setLimitAmount(request.getLimitAmount());
        budget.setResetType(request.getResetType());
        budget.setUpdatedAt(now);

        return budgetRepository.save(budget);
    }

    // ═══════════════════════════════════════════
    //  DELETE  —  ownership check before removing
    // ═══════════════════════════════════════════

    public void delete(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget not found"));
        budgetRepository.delete(budget);
    }

    // ═══════════════════════════════════════════
    //  DELETE BY CATEGORY  —  called from frontend "clear" button
    //  More convenient than deleting by ID when the frontend only knows
    //  the category name, not the MongoDB document ID.
    // ═══════════════════════════════════════════

    public void deleteByCategory(String categoryName) {
        String userId = securityUtils.getCurrentUserEmail();
        String upper = categoryName.toUpperCase().trim();

        Budget budget = budgetRepository
                .findByUserIdAndCategoryName(userId, upper)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No budget found for category: " + upper));

        budgetRepository.delete(budget);
    }
}