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

    private String userId;           // owner — same pattern as Transaction

    private String description;
    private Double amount;
    private Transaction.TransactionType type;
    private Transaction.Category category;
    private Transaction.Division division;

    private Frequency frequency;     // HOW OFTEN it repeats

    private LocalDateTime nextRunDate;  // WHEN to run it next
    private LocalDateTime lastRunDate;  // WHEN it last ran (null if never)
    private LocalDateTime createdAt;

    private boolean active;          // paused or running

    public enum Frequency {
        DAILY, WEEKLY, MONTHLY, YEARLY
    }
}