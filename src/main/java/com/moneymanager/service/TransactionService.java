package com.moneymanager.service;

import com.moneymanager.dto.DashboardResponse;
import com.moneymanager.dto.TransactionRequest;
import com.moneymanager.exception.EditNotAllowedException;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.Transaction;
import com.moneymanager.model.Transaction.Category;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import com.moneymanager.repository.TransactionRepository;
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

    // ===========================
    // CREATE TRANSACTION
    // ===========================
    public Transaction createTransaction(TransactionRequest request) {
        if (request.getDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Transaction date cannot be in the future");
        }

        validateCategory(request.getType(), request.getCategory());

        Transaction transaction = new Transaction();
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

    // ===========================
    // GET ALL
    // ===========================
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    // ===========================
    // GET BY ID
    // ===========================
    public Transaction getTransactionById(String id) {
        return transactionRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Transaction not found with id: " + id));
    }

    // ===========================
    // UPDATE (WITH 12-HOUR RULE)
    // ===========================
    public Transaction updateTransaction(String id, TransactionRequest request) {

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

        return transactionRepository.save(transaction);
    }

    // ===========================
    // DELETE
    // ===========================
    public void deleteTransaction(String id) {
        Transaction transaction = getTransactionById(id);
        transactionRepository.delete(transaction);
    }

    // ===========================
    // FILTER
    // ===========================
    public List<Transaction> getFilteredTransactions(
            Division division,
            Category category,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {

        return transactionRepository.findAll().stream()
                .filter(t -> division == null || t.getDivision().equals(division))
                .filter(t -> category == null || t.getCategory().equals(category))
                .filter(t -> startDate == null || !t.getDate().isBefore(startDate))
                .filter(t -> endDate == null || !t.getDate().isAfter(endDate))
                .collect(Collectors.toList());
    }

    // ===========================
    // DASHBOARD
    // ===========================
    public DashboardResponse getDashboardData() {

        List<Transaction> allTransactions = transactionRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        double totalIncome = calculateTotal(allTransactions, TransactionType.INCOME);
        double totalExpenditure = calculateTotal(allTransactions, TransactionType.EXPENSE);
        double balance = totalIncome - totalExpenditure;

        // Monthly
        LocalDateTime startOfMonth = now.withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0);

        DashboardResponse.SummaryData monthlySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfMonth, now));

        // Weekly
        LocalDateTime startOfWeek = now.minusDays(7);

        DashboardResponse.SummaryData weeklySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfWeek, now));

        // Yearly
        LocalDateTime startOfYear = now.withDayOfYear(1)
                .withHour(0).withMinute(0).withSecond(0);

        DashboardResponse.SummaryData yearlySummary =
                calculateSummary(filterByDateRange(allTransactions, startOfYear, now));

        // Category Summary (Expenses Only)
        Map<String, Double> categorySummary =
                calculateCategorySummary(allTransactions);

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

    // ===========================
    // PRIVATE METHODS
    // ===========================

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
            if (!(category == Category.SALARY ||
                    category == Category.FREELANCE ||
                    category == Category.INVESTMENT ||
                    category == Category.OTHER)) {

                throw new IllegalArgumentException("Invalid category for INCOME transaction");
            }
        }

        if (type == TransactionType.EXPENSE) {
            if (!(category == Category.FUEL ||
                    category == Category.FOOD ||
                    category == Category.MOVIE ||
                    category == Category.LOAN ||
                    category == Category.MEDICAL ||
                    category == Category.OTHER)) {

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

        double income = calculateTotal(transactions, TransactionType.INCOME);
        double expenditure = calculateTotal(transactions, TransactionType.EXPENSE);
        double balance = income - expenditure;

        return new DashboardResponse.SummaryData(income, expenditure, balance);
    }
}
