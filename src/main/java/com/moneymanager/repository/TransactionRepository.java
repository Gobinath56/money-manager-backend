package com.moneymanager.repository;

import com.moneymanager.model.Transaction;
import com.moneymanager.model.Transaction.Category;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    // ═══════════════════════════════════════════════════════════════════════
    //  HOW SPRING DATA DERIVED QUERIES WORK
    // ═══════════════════════════════════════════════════════════════════════
    // Spring reads the method name and generates the MongoDB query for you.
    // Pattern: findBy + <Field> + And + <Field> + ...
    //
    // findByUserId(email)
    //   → db.transactions.find({ userId: email })
    //
    // findByUserIdAndType(email, type)
    //   → db.transactions.find({ userId: email, type: type })
    //
    // findByUserIdAndDateBetween(email, start, end)
    //   → db.transactions.find({ userId: email, date: { $gte: start, $lte: end } })
    //
    // "Between" is a Spring Data keyword that maps to $gte / $lte automatically.
    // No @Query annotation needed unless the logic can't be expressed in the name.
    // ═══════════════════════════════════════════════════════════════════════

    // ── Primary query: all transactions for a user ────────────────────────
    // Used by: getAllTransactions(), getDashboardData(), getFilteredTransactions()
    // Replaces the old findAll() that returned everyone's data.
    List<Transaction> findByUserId(String userId);

    // ── Filter by type (INCOME / EXPENSE) for a user ─────────────────────
    // Used when you want to split income vs expense without loading everything.
    List<Transaction> findByUserIdAndType(String userId, TransactionType type);

    // ── Filter by division for a user ────────────────────────────────────
    // Division = OFFICE or PERSONAL
    List<Transaction> findByUserIdAndDivision(String userId, Division division);

    // ── Filter by category for a user ────────────────────────────────────
    // Category = FOOD, FUEL, SALARY etc.
    List<Transaction> findByUserIdAndCategory(String userId, Category category);

    // ── Filter by date range for a user ──────────────────────────────────
    // "Between" keyword → $gte startDate AND $lte endDate
    List<Transaction> findByUserIdAndDateBetween(
            String userId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // ── Filter by type AND date range for a user ──────────────────────────
    // Useful for: "show me all EXPENSE transactions this month"
    List<Transaction> findByUserIdAndTypeAndDateBetween(
            String userId,
            TransactionType type,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // ── Filter by division AND date range for a user ──────────────────────
    // Useful for: "show me all OFFICE transactions this week"
    List<Transaction> findByUserIdAndDivisionAndDateBetween(
            String userId,
            Division division,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // ── Filter by category AND date range for a user ──────────────────────
    // Useful for: "how much did I spend on FOOD this month?"
    List<Transaction> findByUserIdAndCategoryAndDateBetween(
            String userId,
            Category category,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // ── Count transactions for a user (no data transfer, just a number) ───
    // Useful for: pagination total count, dashboard stats
    long countByUserId(String userId);

    // ── Check if a specific transaction belongs to a user ─────────────────
    // Used as an ownership check before sensitive operations.
    // Returns Optional — empty if ID doesn't exist OR doesn't belong to userId.
    Optional<Transaction> findByIdAndUserId(String id, String userId);
}