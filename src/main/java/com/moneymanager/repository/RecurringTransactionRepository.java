package com.moneymanager.repository;

import com.moneymanager.model.RecurringTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RecurringTransactionRepository
        extends MongoRepository<RecurringTransaction, String> {

    // Get all recurring transactions for a user
    List<RecurringTransaction> findByUserId(String userId);

    // THE KEY QUERY — finds all active items whose nextRunDate has passed
    // Called by the @Scheduled task every midnight
    // "LessThanEqual" → nextRunDate <= now (i.e. due or overdue)
    List<RecurringTransaction> findByActiveIsTrueAndNextRunDateLessThanEqual(
            LocalDateTime now
    );

    // Ownership check — same pattern as Transaction
    Optional<RecurringTransaction> findByIdAndUserId(String id, String userId);
}