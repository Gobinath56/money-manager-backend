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

    @NotNull
    private Frequency frequency;     // DAILY / WEEKLY / MONTHLY / YEARLY

    @NotNull
    private java.time.LocalDateTime startDate; // when should it first run?
}