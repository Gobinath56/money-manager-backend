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
    private final SecurityUtils securityUtils;

    // ═══════════════════════════════════════════
    //  CREATE
    // ═══════════════════════════════════════════

    // In TransactionService.java — inject AccountRepository
    private final AccountRepository accountRepository;

    public Transaction createTransaction(TransactionRequest request) {
        if (request.getDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }
        validateCategory(request.getType(), request.getCategory());

        // ── Find the account this transaction belongs to ──────────────────────
        String userId = securityUtils.getCurrentUserEmail();
        Account account = accountRepository.findByIdAndUserId(
                        request.getAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        // ── Update balance based on transaction type ───────────────────────────
        // INCOME → money coming IN → add to balance
        // EXPENSE → money going OUT → subtract from balance
        if (request.getType() == TransactionType.INCOME) {
            account.setBalance(account.getBalance() + request.getAmount());
        } else {
            if (account.getBalance() < request.getAmount()) {
                throw new IllegalArgumentException("Insufficient balance in account");
            }
            account.setBalance(account.getBalance() - request.getAmount());
        }
        accountRepository.save(account);

        // ── Save the transaction ───────────────────────────────────────────────
        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(request.getAccountId()); // ← link to account
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
    //  GET ALL  —  scoped to current user
    // ═══════════════════════════════════════════

    public List<Transaction> getAllTransactions() {
        // Before fix: transactionRepository.findAll() → returned EVERYONE's data
        // After fix:  findByUserId(email)             → returns only this user's data
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findByUserId(userId);
    }

    // ═══════════════════════════════════════════
    //  GET BY ID  —  ownership check
    // ═══════════════════════════════════════════

    public Transaction getTransactionById(String id) {
        String userId = securityUtils.getCurrentUserEmail();

        return transactionRepository.findById(id)
                // .filter() on Optional: if the userId doesn't match, Optional becomes empty
                // → falls through to orElseThrow → 404
                .filter(t -> userId.equals(t.getUserId()))
                .orElseThrow(() ->
                        new ResourceNotFoundException("Transaction not found with id: " + id));

        // SECURITY NOTE: We return 404 whether the transaction doesn't exist OR
        // belongs to another user. Never return 403 here — that would tell the caller
        // "this ID exists but it's not yours", leaking other users' data.
    }

    // ═══════════════════════════════════════════
    //  UPDATE  —  (12-hour rule + ownership)
    // ═══════════════════════════════════════════

    public Transaction updateTransaction(String id, TransactionRequest request) {

        // getTransactionById already checks ownership — if it passes, we own it
        Transaction transaction = getTransactionById(id);

        checkEditAllowed(transaction);
        validateCategory(request.getType(), request.getCategory());

        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());
        transaction.setDivision(request.getDivision());
        transaction.setDate(request.getDate());
        transaction.setUpdatedAt(LocalDateTime.now());

        // userId is NOT updated — the owner never changes after creation
        return transactionRepository.save(transaction);
    }

    // ═══════════════════════════════════════════
    //  DELETE  —  ownership checked
    // ═══════════════════════════════════════════

    public void deleteTransaction(String id) {
        Transaction transaction = getTransactionById(id);

        // ── Reverse the balance change when deleting ───────────────────────────
        accountRepository.findByIdAndUserId(
                        transaction.getAccountId(),
                        securityUtils.getCurrentUserEmail())
                .ifPresent(account -> {
                    // Reverse: if it was INCOME, remove it. If EXPENSE, add it back.
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

        // Start from only this user's transactions, then apply filters
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

        // Single DB call — get all this user's transactions once,
        // then derive all summaries in-memory (avoids 6+ separate queries)
        List<Transaction> allTransactions = transactionRepository.findByUserId(userId);

        LocalDateTime now = LocalDateTime.now();

        // ── Totals ────────────────────────────────────────────────────────
        double totalIncome       = calculateTotal(allTransactions, TransactionType.INCOME);
        double totalExpenditure  = calculateTotal(allTransactions, TransactionType.EXPENSE);
        double balance           = totalIncome - totalExpenditure;

        // ── Monthly summary (1st of current month → now) ──────────────────
        LocalDateTime startOfMonth = now
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        DashboardResponse.SummaryData monthlySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfMonth, now));

        // ── Weekly summary (last 7 days → now) ────────────────────────────
        LocalDateTime startOfWeek = now.minusDays(7);

        DashboardResponse.SummaryData weeklySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfWeek, now));

        // ── Yearly summary (Jan 1 → now) ──────────────────────────────────
        LocalDateTime startOfYear = now
                .withDayOfYear(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        DashboardResponse.SummaryData yearlySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfYear, now));

        // ── Category breakdown (expenses only) ────────────────────────────
        Map<String, Double> categorySummary = calculateCategorySummary(allTransactions);

        // ── Build and return response ─────────────────────────────────────
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
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════

    /**
     * Throws EditNotAllowedException if more than 12 hours have passed since creation.
     * Duration.between() returns a Duration object — .toHours() converts to whole hours.
     */
    private void checkEditAllowed(Transaction transaction) {
        Duration duration = Duration.between(transaction.getCreatedAt(), LocalDateTime.now());
        if (duration.toHours() > 12) {
            throw new EditNotAllowedException(
                    "You can only edit transactions within 12 hours of creation."
            );
        }
    }

    /**
     * Groups expense transactions by category name, summing amounts.
     * Collectors.groupingBy = SQL GROUP BY
     * Collectors.summingDouble = SQL SUM()
     */
    private Map<String, Double> calculateCategorySummary(List<Transaction> transactions) {
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.groupingBy(
                        t -> t.getCategory().name(),        // key = "FOOD", "FUEL" etc.
                        Collectors.summingDouble(Transaction::getAmount)
                ));
    }

    /**
     * Validates that the chosen category is valid for the given transaction type.
     * Income can only use income categories; expense can only use expense categories.
     */
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

    /**
     * Sums amounts for all transactions of a given type in the list.
     * mapToDouble() converts Stream<Transaction> to DoubleStream so .sum() is available.
     */
    private double calculateTotal(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.getType().equals(type))
                .mapToDouble(Transaction::getAmount)
                .sum();
    }

    /**
     * Filters a list to only transactions whose date falls within [start, end].
     * isBefore/isAfter are exclusive, so we negate them for inclusive bounds.
     * !isBefore(start) means date >= start
     * !isAfter(end)    means date <= end
     */
    private List<Transaction> filterByDateRange(
            List<Transaction> transactions,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return transactions.stream()
                .filter(t -> !t.getDate().isBefore(start) && !t.getDate().isAfter(end))
                .collect(Collectors.toList());
    }

    // Internal method — used by scheduler only, no SecurityContext needed
// "package-private" (no access modifier) means only classes in the same
// package can call it — not accessible from controllers
    Transaction createTransactionInternal(String userId, TransactionRequest request) {
        validateCategory(request.getType(), request.getCategory());

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);               // userId passed directly
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
    /**
     * Calculates income, expenditure, and balance for a list of transactions.
     */

    private DashboardResponse.SummaryData calculateSummary(List<Transaction> transactions) {
        double income       = calculateTotal(transactions, TransactionType.INCOME);
        double expenditure  = calculateTotal(transactions, TransactionType.EXPENSE);
        return new DashboardResponse.SummaryData(income, expenditure, income - expenditure);
    }
}