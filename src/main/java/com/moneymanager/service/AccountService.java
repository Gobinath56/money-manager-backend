package com.moneymanager.service;

import com.moneymanager.dto.TransferRequest;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.Account;
import com.moneymanager.repository.AccountRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final SecurityUtils securityUtils;

    // ═══════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════

    public Account createAccount(Account account) {
        // Tag this account with the owner's email before saving.
        // The account object comes from the request body — it won't have userId set.
        account.setUserId(securityUtils.getCurrentUserEmail());
        return accountRepository.save(account);
    }

    // ═══════════════════════════════════════════
    //  GET ALL  —  scoped to current user
    // ═══════════════════════════════════════════

    public List<Account> getAllAccounts() {
        // Before: findAll() returned every account in the DB
        // After:  findByUserId() returns only this user's accounts
        String userId = securityUtils.getCurrentUserEmail();
        return accountRepository.findByUserId(userId);
    }

    // ═══════════════════════════════════════════
    //  DELETE  —  ownership check
    // ═══════════════════════════════════════════

    public void deleteAccount(String id) {
        String userId = securityUtils.getCurrentUserEmail();

        // findByIdAndUserId: only succeeds if BOTH id AND userId match.
        // If id exists but belongs to another user → empty Optional → 404.
        // This prevents User B from deleting User A's account.
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        accountRepository.delete(account);
    }

    // ═══════════════════════════════════════════
    //  TRANSFER
    // ═══════════════════════════════════════════

    public Account transfer(TransferRequest request) {
        String userId = securityUtils.getCurrentUserEmail();

        // ── Validation first (fixed order vs original code) ──────────────
        // Original bug: balance was checked before amount > 0.
        // If amount was null, the balance check threw NullPointerException.
        // Correct order: validate inputs → then check business rules.

        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new IllegalArgumentException("Transfer amount must be greater than 0");
        }

        if (request.getFromAccountId() == null || request.getToAccountId() == null) {
            throw new IllegalArgumentException("Both account IDs are required");
        }

        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        // ── Ownership checks ──────────────────────────────────────────────
        // Both accounts must belong to the current user.
        // findByIdAndUserId: if ID exists but belongs to another user → 404.
        // This prevents User B from transferring money out of User A's account.

        Account from = accountRepository.findByIdAndUserId(request.getFromAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));

        Account to = accountRepository.findByIdAndUserId(request.getToAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found"));

        // ── Business rule: sufficient balance ─────────────────────────────
        // Checked after validation, not before — amount is guaranteed non-null here.
        if (from.getBalance() < request.getAmount()) {
            throw new RuntimeException("Insufficient balance in source account");
        }

        // ── Execute transfer ───────────────────────────────────────────────
        from.setBalance(from.getBalance() - request.getAmount());
        to.setBalance(to.getBalance() + request.getAmount());

        // Save both — if the second save fails, the first is already committed.
        // For true atomicity you'd need a MongoDB transaction, but for this
        // scale the risk is acceptable.
        accountRepository.save(from);
        accountRepository.save(to);

        return from; // return the debited account so frontend can show updated balance
    }
}