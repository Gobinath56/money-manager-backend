package com.moneymanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recurring_transactions")
public class RecurringTransaction {

    @Id
    private String id;

    private String userId;

    private String description;
    private Double amount;
    private Transaction.TransactionType type;
    private String category;
    private Transaction.Division division;

    // ── BUG FIX: accountId was missing ──────────────────────────────────────
    //
    // THE BUG:
    //   The original RecurringTransaction model had no accountId field.
    //   When the scheduler called createTransactionInternal(), it passed a
    //   TransactionRequest with accountId = null.
    //
    //   This caused two problems:
    //     1. Every auto-created transaction had accountId = null — unlinked
    //        to any account, so they couldn't be associated with a balance.
    //     2. Account balances were never updated by recurring transactions
    //        at all (salary, rent, EMIs had zero effect on any balance).
    //
    // THE FIX:
    //   Add accountId here so the user picks an account when setting up the
    //   recurring transaction. The scheduler then passes this accountId into
    //   createTransactionInternal(), which uses it to update the balance.
    private String accountId;

    private Frequency frequency;

    private LocalDateTime nextRunDate;
    private LocalDateTime lastRunDate;
    private LocalDateTime createdAt;

    private boolean active;

    public enum Frequency {
        DAILY, WEEKLY, MONTHLY, YEARLY
    }
}