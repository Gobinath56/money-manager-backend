package com.moneymanager.dto;

import com.moneymanager.model.RecurringTransaction.Frequency;
import com.moneymanager.model.Transaction.Category;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RecurringTransactionRequest {

    @NotNull
    private String description;

    @NotNull @Positive
    private Double amount;

    @NotNull
    private TransactionType type;

    @NotNull
    private Category category;

    @NotNull
    private Division division;

    // ── BUG FIX: accountId was missing ──────────────────────────────────────
    //
    // THE BUG:
    //   RecurringTransactionRequest had no accountId. So when a user created
    //   a recurring transaction, there was no way to associate it with any
    //   account. The scheduler then created real Transaction records with
    //   accountId = null, and never updated any account balance.
    //
    // THE FIX:
    //   Add accountId here so the frontend can send it when creating a
    //   recurring transaction. The RecurringTransactionService.create()
    //   method saves it onto the RecurringTransaction document. The scheduler
    //   later reads it when auto-firing the transaction.
    @NotNull(message = "Account is required")
    private String accountId;

    @NotNull
    private Frequency frequency;

    @NotNull
    private java.time.LocalDateTime startDate;
}