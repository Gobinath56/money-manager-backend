package com.moneymanager.service;

import com.moneymanager.dto.DashboardResponse;
import com.moneymanager.dto.TransactionRequest;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.Account;
import com.moneymanager.model.Transaction;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import com.moneymanager.repository.AccountRepository;
import com.moneymanager.repository.TransactionRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository     accountRepository;
    private final SecurityUtils         securityUtils;

    // ═══════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════

    public Transaction createTransaction(TransactionRequest request) {
        if (request.getDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }

        String userId = securityUtils.getCurrentUserEmail();

        Account account = accountRepository
                .findByIdAndUserId(request.getAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (request.getType() == TransactionType.INCOME) {
            account.setBalance(account.getBalance() + request.getAmount());
        } else {
            // FIX: strict balance floor — never allow negative balance
            double newBalance = account.getBalance() - request.getAmount();
            if (newBalance < 0) {
                throw new IllegalArgumentException(
                        "Insufficient balance. Available: ₹" +
                                String.format("%.2f", account.getBalance()) +
                                ", Required: ₹" + String.format("%.2f", request.getAmount())
                );
            }
            account.setBalance(newBalance);
        }
        accountRepository.save(account);

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
    //  GET ALL
    // ═══════════════════════════════════════════

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findByUserId(securityUtils.getCurrentUserEmail());
    }

    // ═══════════════════════════════════════════
    //  GET BY ID
    // ═══════════════════════════════════════════

    public Transaction getTransactionById(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findById(id)
                .filter(t -> userId.equals(t.getUserId()))
                .orElseThrow(() ->
                        new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    // ═══════════════════════════════════════════
    //  UPDATE
    //
    //  FIX: Proper atomic balance correction.
    //  Step 1: reverse old transaction effect on old account.
    //  Step 2: validate new balance BEFORE applying.
    //  Step 3: apply new transaction effect.
    //  If step 3 fails, step 1 is rolled back so data stays consistent.
    // ═══════════════════════════════════════════

    public Transaction updateTransaction(String id, TransactionRequest request) {
        Transaction transaction = getTransactionById(id);
        String userId = securityUtils.getCurrentUserEmail();

        // ── Step 1: reverse the old transaction on the old account ─────────
        Account oldAccount = null;
        if (transaction.getAccountId() != null) {
            oldAccount = accountRepository
                    .findByIdAndUserId(transaction.getAccountId(), userId)
                    .orElse(null);

            if (oldAccount != null) {
                if (transaction.getType() == TransactionType.INCOME) {
                    // reversing income: subtract it back
                    double reversedBalance = oldAccount.getBalance() - transaction.getAmount();
                    // FIX: even reversing income cannot push balance below 0
                    // (it shouldn't normally, but guards against data corruption)
                    oldAccount.setBalance(Math.max(0, reversedBalance));
                } else {
                    // reversing expense: add it back
                    oldAccount.setBalance(oldAccount.getBalance() + transaction.getAmount());
                }
                accountRepository.save(oldAccount);
            }
        }

        // ── Step 2: validate and apply on the new account ──────────────────
        // FIX: capture oldAccount in a final variable so it can be used inside lambda
        final Account finalOldAccount = oldAccount;
        Account newAccount = accountRepository
                .findByIdAndUserId(request.getAccountId(), userId)
                .orElseThrow(() -> {
                    // rollback step 1 before throwing
                    rollbackOldAccount(finalOldAccount, transaction);
                    return new ResourceNotFoundException("Account not found");
                });

        if (request.getType() == TransactionType.INCOME) {
            newAccount.setBalance(newAccount.getBalance() + request.getAmount());
        } else {
            double newBalance = newAccount.getBalance() - request.getAmount();
            if (newBalance < 0) {
                // FIX: rollback step 1 before throwing so data stays consistent
                rollbackOldAccount(finalOldAccount, transaction);
                throw new IllegalArgumentException(
                        "Insufficient balance after update. Available: ₹" +
                                String.format("%.2f", newAccount.getBalance()) +
                                ", Required: ₹" + String.format("%.2f", request.getAmount())
                );
            }
            newAccount.setBalance(newBalance);
        }
        accountRepository.save(newAccount);

        // ── Step 3: persist the updated transaction ────────────────────────
        transaction.setAccountId(request.getAccountId());
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());
        transaction.setSubCategory(request.getSubCategory());
        transaction.setDivision(request.getDivision());
        transaction.setDate(request.getDate());
        transaction.setUpdatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
    }

    // ── Rollback helper: re-apply old transaction effect if step 2 fails ──
    private void rollbackOldAccount(Account oldAccount, Transaction oldTx) {
        if (oldAccount == null) return;
        try {
            if (oldTx.getType() == TransactionType.INCOME) {
                oldAccount.setBalance(oldAccount.getBalance() + oldTx.getAmount());
            } else {
                oldAccount.setBalance(Math.max(0, oldAccount.getBalance() - oldTx.getAmount()));
            }
            accountRepository.save(oldAccount);
        } catch (Exception e) {
            log.error("Failed to rollback old account {} during update: {}", oldAccount.getId(), e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  DELETE
    // ═══════════════════════════════════════════

    public void deleteTransaction(String id) {
        Transaction transaction = getTransactionById(id);

        accountRepository.findByIdAndUserId(
                        transaction.getAccountId(),
                        securityUtils.getCurrentUserEmail())
                .ifPresent(account -> {
                    if (transaction.getType() == TransactionType.INCOME) {
                        // reversing income: subtract — but floor at 0
                        account.setBalance(Math.max(0, account.getBalance() - transaction.getAmount()));
                    } else {
                        // reversing expense: add back
                        account.setBalance(account.getBalance() + transaction.getAmount());
                    }
                    accountRepository.save(account);
                });

        transactionRepository.delete(transaction);
    }

    // ═══════════════════════════════════════════
    //  FILTER
    // ═══════════════════════════════════════════

    public List<Transaction> getFilteredTransactions(
            Division division,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findByUserId(userId).stream()
                .filter(t -> division  == null || t.getDivision().equals(division))
                .filter(t -> category  == null || category.equals(t.getCategory()))
                .filter(t -> startDate == null || !t.getDate().isBefore(startDate))
                .filter(t -> endDate   == null || !t.getDate().isAfter(endDate))
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════
    //  DASHBOARD
    // ═══════════════════════════════════════════

    public DashboardResponse getDashboardData() {
        String userId = securityUtils.getCurrentUserEmail();
        List<Transaction> allTransactions = transactionRepository.findByUserId(userId);
        LocalDateTime now = LocalDateTime.now();

        double totalIncome      = calculateTotal(allTransactions, TransactionType.INCOME);
        double totalExpenditure = calculateTotal(allTransactions, TransactionType.EXPENSE);

        List<Account> userAccounts = accountRepository.findByUserId(userId);
        double balance = userAccounts.stream()
                .mapToDouble(a -> a.getBalance() != null ? a.getBalance() : 0.0)
                .sum();

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
    //  INTERNAL — called by recurring scheduler
    // ═══════════════════════════════════════════

    public Transaction createTransactionInternal(String userId, TransactionRequest request) {
        if (request.getAccountId() != null) {
            Account account = accountRepository
                    .findByIdAndUserId(request.getAccountId(), userId)
                    .orElse(null);

            if (account != null) {
                if (request.getType() == TransactionType.INCOME) {
                    account.setBalance(account.getBalance() + request.getAmount());
                    accountRepository.save(account);
                } else {
                    double newBalance = account.getBalance() - request.getAmount();
                    if (newBalance >= 0) {
                        // FIX: only deduct if balance stays >= 0
                        account.setBalance(newBalance);
                        accountRepository.save(account);
                    } else {
                        log.warn("Recurring transaction skipped balance deduction for account {} — insufficient funds", account.getId());
                    }
                }
            }
        }

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(request.getAccountId());
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

    private Map<String, Double> calculateCategorySummary(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,
                        Collectors.summingDouble(Transaction::getAmount)
                ));
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