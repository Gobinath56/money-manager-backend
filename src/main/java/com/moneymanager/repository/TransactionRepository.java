package com.moneymanager.repository;

import com.moneymanager.model.Transaction;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {

    // ── Paginated query — used by TransactionsPage (the main list) ────────
    //
    // FIX #5 — was findByUserId(String) which loads ALL transactions into
    // memory on every request. With 1000+ records this causes:
    //   - Slow API response (all data transferred at once)
    //   - High memory usage on the server
    //   - Slow frontend render (React processes all rows)
    //
    // Now: Page<Transaction> findByUserId(String, Pageable)
    //   Spring Data automatically adds SKIP + LIMIT to the MongoDB query.
    //   The frontend sends ?page=0&size=25 and gets back exactly 25 records
    //   plus the total count so it can render page numbers.
    //
    // The original findByUserId(String) is kept below for:
    //   - getDashboardData() which needs ALL transactions for sum calculations
    //   - getFilteredTransactions() which filters in memory
    //   These are aggregate operations that genuinely need all records.
    Page<Transaction> findByUserId(String userId, Pageable pageable);

    // ── Full list — still needed for dashboard aggregations ───────────────
    List<Transaction> findByUserId(String userId);

    List<Transaction> findByUserIdAndType(String userId, TransactionType type);
    List<Transaction> findByUserIdAndDivision(String userId, Division division);
    List<Transaction> findByUserIdAndCategory(String userId, String category);

    List<Transaction> findByUserIdAndDateBetween(
            String userId, LocalDateTime startDate, LocalDateTime endDate);

    List<Transaction> findByUserIdAndTypeAndDateBetween(
            String userId, TransactionType type,
            LocalDateTime startDate, LocalDateTime endDate);

    List<Transaction> findByUserIdAndDivisionAndDateBetween(
            String userId, Division division,
            LocalDateTime startDate, LocalDateTime endDate);

    List<Transaction> findByUserIdAndCategoryAndDateBetween(
            String userId, String category,
            LocalDateTime startDate, LocalDateTime endDate);

    long countByUserId(String userId);

    Optional<Transaction> findByIdAndUserId(String id, String userId);
}