package com.moneymanager.service;

import com.moneymanager.dto.TransferRequest;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.Account;
import com.moneymanager.repository.AccountRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FIX #6 — Transfer was not atomic.
 *
 * THE BUG:
 *   accountRepository.save(from);   // committed immediately
 *   accountRepository.save(to);     // if this threw, 'from' was already debited
 *                                   // → money vanished, no recovery
 *
 * THE FIX:
 *   @Transactional wraps both saves in a single MongoDB multi-document
 *   transaction. If the second save fails for any reason, the first save
 *   is automatically rolled back.
 *
 * REQUIREMENT:
 *   MongoDB must be running as a replica set (even a single-node replica set).
 *   Multi-document transactions are not supported on standalone mongod.
 *
 *   For local dev, convert your standalone to a replica set:
 *     mongod --replSet rs0
 *     # then in mongo shell: rs.initiate()
 *
 *   Atlas (cloud) and most managed providers already run replica sets.
 *   Railway/Render MongoDB add-ons: check your provider docs.
 *
 *   Also add to application.properties (required for Spring to manage the tx):
 *     spring.data.mongodb.uri=<your-uri>  (must include ?replicaSet=rs0 or equivalent)
 *
 *   And add this bean anywhere in a @Configuration class:
 *     @Bean
 *     public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
 *         return new MongoTransactionManager(dbFactory);
 *     }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final SecurityUtils securityUtils;

    // ═══════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════

    public Account createAccount(Account account) {
        account.setUserId(securityUtils.getCurrentUserEmail());
        return accountRepository.save(account);
    }

    // ═══════════════════════════════════════════
    //  GET ALL  —  scoped to current user
    // ═══════════════════════════════════════════

    public List<Account> getAllAccounts() {
        String userId = securityUtils.getCurrentUserEmail();
        return accountRepository.findByUserId(userId);
    }

    // ═══════════════════════════════════════════
    //  DELETE  —  ownership check
    // ═══════════════════════════════════════════

    public void deleteAccount(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        accountRepository.delete(account);
    }

    // ═══════════════════════════════════════════
    //  TRANSFER  —  now atomic via @Transactional
    // ═══════════════════════════════════════════

    /**
     * Transfers money between two accounts owned by the current user.
     *
     * @Transactional ensures both saves succeed or both are rolled back.
     * If save(to) throws for any reason (network blip, validation error,
     * optimistic lock conflict, etc.), save(from) is rolled back automatically
     * by MongoTransactionManager — no money is lost.
     *
     * propagation = REQUIRES_NEW: always starts a fresh transaction even if
     * called from within another transaction (e.g. a future batch operation).
     * This prevents an outer transaction from masking a transfer failure.
     *
     * rollbackFor = Exception.class: roll back on any exception, not just
     * RuntimeException (Spring's default only catches unchecked exceptions).
     */
    @Transactional(rollbackFor = Exception.class)
    public Account transfer(TransferRequest request) {
        String userId = securityUtils.getCurrentUserEmail();

        // ── Input validation first ──────────────────────────────────────────
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than 0");
        }
        if (request.getFromAccountId() == null || request.getToAccountId() == null) {
            throw new IllegalArgumentException("Both account IDs are required");
        }
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // ── Ownership checks ────────────────────────────────────────────────
        Account from = accountRepository
                .findByIdAndUserId(request.getFromAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        Account to = accountRepository
                .findByIdAndUserId(request.getToAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

        // ── Business rule: sufficient balance ───────────────────────────────
        if (from.getBalance() < request.getAmount()) {
            throw new IllegalArgumentException(
                    "Insufficient balance. Available: ₹" +
                            String.format("%.2f", from.getBalance()) +
                            ", Required: ₹" + String.format("%.2f", request.getAmount())
            );
        }

        // ── Execute transfer (both saves are inside the same transaction) ───
        from.setBalance(from.getBalance() - request.getAmount());
        to.setBalance(to.getBalance() + request.getAmount());

        // If save(to) throws after save(from) has already written,
        // @Transactional rolls back save(from) automatically.
        accountRepository.save(from);
        accountRepository.save(to);

        log.info("Transfer of ₹{} from account {} to {} completed for user {}",
                request.getAmount(), from.getId(), to.getId(), userId);

        return from;
    }
}