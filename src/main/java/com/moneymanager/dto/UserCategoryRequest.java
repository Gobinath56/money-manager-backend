package com.moneymanager.dto;

import com.moneymanager.model.Transaction.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class UserCategoryRequest {

    @NotBlank
    private String name;              // e.g. "TRIP"

    @NotNull
    private TransactionType type;     // INCOME or EXPENSE

    private List<String> subCategories; // ["Travel", "Hotel", "Food"]
}