package com.moneymanager.repository;

import com.moneymanager.model.Budget;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends MongoRepository<Budget, String> {

    /** All budgets for a user — used to load the Budget page */
    List<Budget> findByUserId(String userId);

    /** Find a specific category budget for ownership checks + upsert logic */
    Optional<Budget> findByUserIdAndCategoryName(String userId, String categoryName);

    /** Ownership check before delete */
    Optional<Budget> findByIdAndUserId(String id, String userId);

    /** Delete all budgets for a user (called if account is ever deleted) */
    void deleteByUserId(String userId);
}