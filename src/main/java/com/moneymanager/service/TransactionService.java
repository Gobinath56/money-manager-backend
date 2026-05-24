package com.moneymanager.service;

import com.moneymanager.dto.DashboardResponse;
import com.moneymanager.dto.TransactionRequest;
import com.moneymanager.exception.EditNotAllowedException;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.Account;
import com.moneymanager.model.Transaction;
import com.moneymanager.model.Transaction.Category;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import com.moneymanager.repository.AccountRepository;
import com.moneymanager.repository.TransactionRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final SecurityUtils securityUtils;

    // ═══════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════

    public Transaction createTransaction(TransactionRequest request) {
        if (request.getDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }
        validateCategory(request.getType(), request.getCategory());

        String userId = securityUtils.getCurrentUserEmail();

        // Find the account — must belong to this user
        Account account = accountRepository.findByIdAndUserId(request.getAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // Update balance: INCOME adds, EXPENSE subtracts
        if (request.getType() == TransactionType.INCOME) {
            account.setBalance(account.getBalance() + request.getAmount());
        } else {
            if (account.getBalance() < request.getAmount()) {
                throw new IllegalArgumentException("Insufficient balance in account");
            }
            account.setBalance(account.getBalance() - request.getAmount());
        }
        accountRepository.save(account);

        // Save the transaction
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(request.getAccountId());
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());
        transaction.setSubCategory(request.getSubCategory());
        transaction.setDivision(request.getDivision());
        transaction.setDate(request.getDate());
        LocalDateTime now = LocalDateTime.now();
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);

        return transactionRepository.save(transaction);
    }

    // ═══════════════════════════════════════════
    //  GET ALL  —  scoped to current user
    // ═══════════════════════════════════════════

    public List<Transaction> getAllTransactions() {
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findByUserId(userId);
    }

    // ═══════════════════════════════════════════
    //  GET BY ID  —  ownership check
    // ═══════════════════════════════════════════

    public Transaction getTransactionById(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findById(id)
                .filter(t -> userId.equals(t.getUserId()))
                .orElseThrow(() ->
                        new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    // ═══════════════════════════════════════════
    //  UPDATE  —  BUG FIX: reverse old balance effect, apply new one
    //
    //  THE BUG (before fix):
    //    updateTransaction() only saved new field values onto the transaction
    //    document but never touched the Account balance at all.
    //
    //  Example of silent data corruption:
    //    - User logs EXPENSE ₹100 → account balance correctly drops by ₹100
    //    - User edits it to ₹500 → only the Transaction doc changes to ₹500
    //    - Account balance still reflects the old ₹100 deduction → wrong by ₹400
    //    - User edits type from EXPENSE → INCOME → balance is now completely wrong
    //
    //  THE FIX:
    //    Step 1 — Reverse the OLD transaction's effect on the account balance.
    //             If the old transaction was EXPENSE ₹100, add ₹100 back.
    //             If it was INCOME ₹100, subtract ₹100 back.
    //    Step 2 — Apply the NEW transaction's effect.
    //             This handles all combinations: same type + new amount,
    //             type flip (INCOME→EXPENSE), or account change.
    //    Step 3 — Save the updated account(s) and the transaction.
    // ═══════════════════════════════════════════

    public Transaction updateTransaction(String id, TransactionRequest request) {
        // Ownership check is inside getTransactionById
        Transaction transaction = getTransactionById(id);

        checkEditAllowed(transaction);
        validateCategory(request.getType(), request.getCategory());

        String userId = securityUtils.getCurrentUserEmail();

        // ── STEP 1: Reverse the OLD transaction's balance effect ────────────
        // We must undo what the original transaction did to the account,
        // regardless of whether the account itself is being changed.
        if (transaction.getAccountId() != null) {
            accountRepository.findByIdAndUserId(transaction.getAccountId(), userId)
                    .ifPresent(oldAccount -> {
                        // Undo: INCOME had added money → subtract it back
                        //       EXPENSE had removed money → add it back
                        if (transaction.getType() == TransactionType.INCOME) {
                            oldAccount.setBalance(oldAccount.getBalance() - transaction.getAmount());
                        } else {
                            oldAccount.setBalance(oldAccount.getBalance() + transaction.getAmount());
                        }
                        accountRepository.save(oldAccount);
                    });
        }

        // ── STEP 2: Apply the NEW transaction's balance effect ──────────────
        // The new request may have a different accountId, different amount,
        // or a different type — we handle all of that here.
        Account newAccount = accountRepository.findByIdAndUserId(request.getAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (request.getType() == TransactionType.INCOME) {
            newAccount.setBalance(newAccount.getBalance() + request.getAmount());
        } else {
            if (newAccount.getBalance() < request.getAmount()) {
                // Balance check failed — IMPORTANT: the old balance was already reversed above.
                // We must re-apply the old transaction to keep the account consistent
                // before throwing, otherwise the account is left in a partially updated state.
                accountRepository.findByIdAndUserId(transaction.getAccountId(), userId)
                        .ifPresent(oldAccount -> {
                            if (transaction.getType() == TransactionType.INCOME) {
                                oldAccount.setBalance(oldAccount.getBalance() + transaction.getAmount());
                            } else {
                                oldAccount.setBalance(oldAccount.getBalance() - transaction.getAmount());
                            }
                            accountRepository.save(oldAccount);
                        });
                throw new IllegalArgumentException("Insufficient balance in account");
            }
            newAccount.setBalance(newAccount.getBalance() - request.getAmount());
        }
        accountRepository.save(newAccount);

        // ── STEP 3: Update the transaction record ───────────────────────────
        transaction.setAccountId(request.getAccountId());
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());
        transaction.setSubCategory(request.getSubCategory());
        transaction.setDivision(request.getDivision());
        transaction.setDate(request.getDate());
        transaction.setUpdatedAt(LocalDateTime.now());
        // userId is never changed — owner doesn't change after creation

        return transactionRepository.save(transaction);
    }

    // ═══════════════════════════════════════════
    //  DELETE  —  reverses balance correctly
    // ═══════════════════════════════════════════

    public void deleteTransaction(String id) {
        Transaction transaction = getTransactionById(id);

        // Reverse the balance change when deleting
        accountRepository.findByIdAndUserId(
                        transaction.getAccountId(),
                        securityUtils.getCurrentUserEmail())
                .ifPresent(account -> {
                    // Reverse: INCOME had added → subtract it back
                    //          EXPENSE had removed → add it back
                    if (transaction.getType() == TransactionType.INCOME) {
                        account.setBalance(account.getBalance() - transaction.getAmount());
                    } else {
                        account.setBalance(account.getBalance() + transaction.getAmount());
                    }
                    accountRepository.save(account);
                });

        transactionRepository.delete(transaction);
    }

    // ═══════════════════════════════════════════
    //  FILTER  —  scoped to current user
    // ═══════════════════════════════════════════

    public List<Transaction> getFilteredTransactions(
            Division division,
            Category category,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findByUserId(userId).stream()
                .filter(t -> division  == null || t.getDivision().equals(division))
                .filter(t -> category  == null || t.getCategory().equals(category))
                .filter(t -> startDate == null || !t.getDate().isBefore(startDate))
                .filter(t -> endDate   == null || !t.getDate().isAfter(endDate))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════
    //  DASHBOARD  —  scoped to current user
    // ═══════════════════════════════════════════

    public DashboardResponse getDashboardData() {
        String userId = securityUtils.getCurrentUserEmail();
        List<Transaction> allTransactions = transactionRepository.findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();

        double totalIncome      = calculateTotal(allTransactions, TransactionType.INCOME);
        double totalExpenditure = calculateTotal(allTransactions, TransactionType.EXPENSE);
        double balance          = totalIncome - totalExpenditure;

        LocalDateTime startOfMonth = now.withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        DashboardResponse.SummaryData monthlySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfMonth, now));

        LocalDateTime startOfWeek = now.minusDays(7);
        DashboardResponse.SummaryData weeklySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfWeek, now));

        LocalDateTime startOfYear = now.withDayOfYear(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        DashboardResponse.SummaryData yearlySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfYear, now));

        Map<String, Double> categorySummary = calculateCategorySummary(allTransactions);

        DashboardResponse response = new DashboardResponse();
        response.setTotalIncome(totalIncome);
        response.setTotalExpenditure(totalExpenditure);
        response.setBalance(balance);
        response.setTransactions(allTransactions);
        response.setMonthlySummary(monthlySummary);
        response.setWeeklySummary(weeklySummary);
        response.setYearlySummary(yearlySummary);
        response.setCategorySummary(categorySummary);

        return response;
    }

    // ═══════════════════════════════════════════
    //  INTERNAL METHOD  —  used by scheduler only
    //
    //  THE BUG (before fix):
    //    This method created a Transaction record but never updated the account
    //    balance. Every recurring transaction (salary, rent, EMI) was silently
    //    recorded without affecting any account's balance.
    //
    //  Also: accountId was never set on the Transaction, so every auto-created
    //    transaction had accountId = null, making them unlinked to any account.
    //
    //  THE FIX:
    //    RecurringTransaction now carries an accountId (see RecurringTransaction.java fix).
    //    This method uses that accountId to find the account and update its balance,
    //    exactly like createTransaction() does — but without reading from SecurityContext
    //    (since the scheduler runs with no HTTP request and no security context).
    // ═══════════════════════════════════════════

    public Transaction createTransactionInternal(String userId, TransactionRequest request) {
        validateCategory(request.getType(), request.getCategory());

        // ── Update account balance (same logic as createTransaction) ─────────
        // accountId comes from the RecurringTransaction — it was set when the
        // user originally created the recurring transaction.
        if (request.getAccountId() != null) {
            accountRepository.findByIdAndUserId(request.getAccountId(), userId)
                    .ifPresent(account -> {
                        if (request.getType() == TransactionType.INCOME) {
                            account.setBalance(account.getBalance() + request.getAmount());
                        } else if (account.getBalance() >= request.getAmount()) {
                            account.setBalance(account.getBalance() - request.getAmount());
                        }
                        // If insufficient balance for an auto-run, we skip the deduction
                        // rather than throwing — a scheduler failure for one user should
                        // not crash the whole processAllDue() batch.
                        accountRepository.save(account);
                    });
        }

        // ── Save the transaction ─────────────────────────────────────────────
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(request.getAccountId()); // now correctly set
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());
        transaction.setDivision(request.getDivision());
        transaction.setDate(request.getDate());
        LocalDateTime now = LocalDateTime.now();
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);

        return transactionRepository.save(transaction);
    }

    // ═══════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════

    private void checkEditAllowed(Transaction transaction) {
        Duration duration = Duration.between(transaction.getCreatedAt(), LocalDateTime.now());
        if (duration.toHours() > 12) {
            throw new EditNotAllowedException(
                    "You can only edit transactions within 12 hours of creation."
            );
        }
    }

    private Map<String, Double> calculateCategorySummary(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().name(),
                        Collectors.summingDouble(Transaction::getAmount)
                ));
    }

    private void validateCategory(TransactionType type, Category category) {
        if (type == TransactionType.INCOME) {
            EnumSet<Category> incomeCategories = EnumSet.of(
                    Category.SALARY, Category.FREELANCE, Category.INVESTMENT, Category.OTHER
            );
            if (!incomeCategories.contains(category)) {
                throw new IllegalArgumentException("Invalid category for INCOME transaction");
            }
        }
        if (type == TransactionType.EXPENSE) {
            EnumSet<Category> expenseCategories = EnumSet.of(
                    Category.FUEL, Category.FOOD, Category.MOVIE,
                    Category.LOAN, Category.MEDICAL, Category.OTHER
            );
            if (!expenseCategories.contains(category)) {
                throw new IllegalArgumentException("Invalid category for EXPENSE transaction");
            }
        }
    }

    private double calculateTotal(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType().equals(type))
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    private List<Transaction> filterByDateRange(
            List<Transaction> transactions,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return transactions.stream()
                .filter(t -> !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
                .collect(Collectors.toList());
    }

    private DashboardResponse.SummaryData calculateSummary(List<Transaction> transactions) {
        double income      = calculateTotal(transactions, TransactionType.INCOME);
        double expenditure = calculateTotal(transactions, TransactionType.EXPENSE);
        return new DashboardResponse.SummaryData(income, expenditure, income - expenditure);
    }
}