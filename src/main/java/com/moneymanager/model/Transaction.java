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
@Document(collection = "transactions")
public class Transaction {
    
    @Id
    private String id;

    private String userId;

    private TransactionType type; // INCOME or EXPENSE
    
    private Double amount;
    
    private String description;
    
    private Category category;
    
    private Division division;
    
    private LocalDateTime date;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    public enum TransactionType {
        INCOME, EXPENSE
    }
    
    public enum Division {
        OFFICE, PERSONAL
    }
    
    public enum Category {
        // Expense categories
        FUEL, MOVIE, FOOD, LOAN, MEDICAL,
        // Income categories  
        SALARY, FREELANCE, INVESTMENT,
        // Common
        OTHER
    }
}
