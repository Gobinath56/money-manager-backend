package com.moneymanager.dto;

import com.moneymanager.model.Budget.ResetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class BudgetRequest {

    @NotBlank(message = "Category name is required")
    private String categoryName;

    @NotNull(message = "Limit amount is required")
    @Positive(message = "Limit must be greater than 0")
    private Double limitAmount;

    @NotNull(message = "Reset type is required (MONTHLY or FIXED)")
    private ResetType resetType;
}