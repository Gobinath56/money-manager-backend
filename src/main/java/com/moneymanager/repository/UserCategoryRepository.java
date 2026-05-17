package com.moneymanager.repository;

import com.moneymanager.model.UserCategory;
import com.moneymanager.model.Transaction.TransactionType;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface UserCategoryRepository extends MongoRepository<UserCategory, String> {

    // Get all categories for a user (custom + their defaults)
    List<UserCategory> findByUserId(String userId);

    // Get by type — INCOME or EXPENSE
    List<UserCategory> findByUserIdAndType(String userId, TransactionType type);

    // Check if name already exists for this user
    Optional<UserCategory> findByUserIdAndName(String userId, String name);

    // Ownership check
    Optional<UserCategory> findByIdAndUserId(String id, String userId);
}