package com.moneymanager.service;

import com.moneymanager.dto.DashboardResponse;
import com.moneymanager.dto.TransactionRequest;
import com.moneymanager.exception.EditNotAllowedException;
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
import java.util.Optional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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

        String userId = securityUtils.getCurrentUserEmail();

        Account account = accountRepository.findByIdAndUserId(request.getAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (request.getType() == TransactionType.INCOME) {
            account.setBalance(account.getBalance() + request.getAmount());
        } else {
            if (account.getBalance() < request.getAmount()) {
                throw new IllegalArgumentException("Insufficient balance in account");
            }
            account.setBalance(account.getBalance() - request.getAmount());
        }
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setUserId(userId);
        transaction.setAccountId(request.getAccountId());
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());   // now a String
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
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findByUserId(userId);
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
    // ═══════════════════════════════════════════

    public Transaction updateTransaction(String id, TransactionRequest request) {
        Transaction transaction = getTransactionById(id);
        checkEditAllowed(transaction);

        String userId = securityUtils.getCurrentUserEmail();

        // Step 1 — reverse old balance effect
        if (transaction.getAccountId() != null) {
            accountRepository.findByIdAndUserId(transaction.getAccountId(), userId)
                    .ifPresent(oldAccount -> {
                        if (transaction.getType() == TransactionType.INCOME) {
                            oldAccount.setBalance(oldAccount.getBalance() - transaction.getAmount());
                        } else {
                            oldAccount.setBalance(oldAccount.getBalance() + transaction.getAmount());
                        }
                        accountRepository.save(oldAccount);
                    });
        }

        // Step 2 — apply new balance effect
        Account newAccount = accountRepository.findByIdAndUserId(request.getAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (request.getType() == TransactionType.INCOME) {
            newAccount.setBalance(newAccount.getBalance() + request.getAmount());
        } else {
            if (newAccount.getBalance() < request.getAmount()) {
                // Re-apply old transaction before throwing to keep data consistent
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

        // Step 3 — update transaction record
        transaction.setAccountId(request.getAccountId());
        transaction.setType(request.getType());
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setCategory(request.getCategory());   // String
        transaction.setSubCategory(request.getSubCategory());
        transaction.setDivision(request.getDivision());
        transaction.setDate(request.getDate());
        transaction.setUpdatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
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
                        account.setBalance(account.getBalance() - transaction.getAmount());
                    } else {
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
            String category,          // String, not enum
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        String userId = securityUtils.getCurrentUserEmail();
        return transactionRepository.findByUserId(userId).stream()
                .filter(t -> division  == null || t.getDivision().equals(division))
                .filter(t -> category  == null || category.equals(t.getCategory()))  // String.equals
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

        // FIX: read real account balances instead of calculating income - expense
        List<Account> userAccounts = accountRepository.findByUserId(userId);
        double balance = userAccounts.stream()
                .mapToDouble(a -> a.getBalance() != null ? a.getBalance() : 0.0)
                .sum();

        // rest stays exactly the same...
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
        response.setBalance(balance);  // now uses real account balance sum
        response.setTransactions(allTransactions);
        response.setMonthlySummary(monthlySummary);
        response.setWeeklySummary(weeklySummary);
        response.setYearlySummary(yearlySummary);
        response.setCategorySummary(categorySummary);

        return response;
    }

    // ═══════════════════════════════════════════
    //  INTERNAL — used by recurring scheduler
    // ═══════════════════════════════════════════

    public Transaction createTransactionInternal(String userId, TransactionRequest request) {

        // Update account balance
        if (request.getAccountId() != null) {

            Account account = accountRepository
                    .findByIdAndUserId(request.getAccountId(), userId)
                    .orElse(null);

            if (account != null) {
                if (request.getType() == TransactionType.INCOME) {
                    account.setBalance(account.getBalance() + request.getAmount());
                    accountRepository.save(account);
                } else {
                    if (account.getBalance() >= request.getAmount()) {
                        account.setBalance(account.getBalance() - request.getAmount());
                        accountRepository.save(account);
                    }
                    // if insufficient balance, we skip but still record the transaction
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
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Transaction::getCategory,          // category is now a String field
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