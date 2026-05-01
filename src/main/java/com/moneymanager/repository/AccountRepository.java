package com.moneymanager.repository;

import com.moneymanager.model.Account;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends MongoRepository<Account, String> {

    // ── Get all accounts belonging to a user ──────────────────────────────
    // Replaces findAll() in AccountService.
    // Generated query: db.accounts.find({ userId: userId })
    List<Account> findByUserId(String userId);

    // ── Get a specific account only if it belongs to this user ───────────
    // This is the ownership check pattern:
    //   findById(id)           → finds any account with that ID (no ownership)
    //   findByIdAndUserId(...) → finds only if BOTH conditions match
    //
    // If ID exists but userId doesn't match → Optional.empty() → 404
    // Used in: deleteAccount(), transfer() in AccountService
    Optional<Account> findByIdAndUserId(String id, String userId);
}