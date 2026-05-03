package com.moneymanager.dto;

import com.moneymanager.model.Transaction.Category;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {
    
    @NotNull(message = "Transaction type is required")
    private TransactionType type;
    
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private Double amount;
    
    @NotNull(message = "Description is required")
    private String description;
    
    @NotNull(message = "Category is required")
    private Category category;

    @NotNull(message = "Account is required")
    private String accountId;

    @NotNull(message = "Division is required")
    private Division division;
    
    @NotNull(message = "Date is required")
    private LocalDateTime date;
}
