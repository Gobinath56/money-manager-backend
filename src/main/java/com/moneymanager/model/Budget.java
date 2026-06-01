package com.moneymanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

/**
 * FIX #18 — Budget limits were only stored in localStorage.
 *
 * Problems with localStorage:
 *   - Not synced across devices (phone + laptop show different limits)
 *   - Cleared when the user clears browser data
 *   - Not visible to the backend (can't enforce server-side budget alerts)
 *
 * Each Budget document represents one spending limit for one category,
 * owned by one user.
 *
 * @CompoundIndex enforces uniqueness: a user can have at most one budget
 * per (categoryName, resetType) combination, preventing duplicates if
 * the frontend sends the save request twice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "budgets")
@CompoundIndex(
        name  = "user_category_unique",
        def   = "{'userId': 1, 'categoryName': 1, 'resetType': 1}",
        unique = true
)
public class Budget {

    @Id
    private String id;

    /** Email of the owning user — matches userId pattern used everywhere else */
    private String userId;

    /** Category name in uppercase, e.g. "FOOD", "FUEL", "TRIP" */
    private String categoryName;

    /** Spending limit in rupees */
    private Double limitAmount;

    /**
     * MONTHLY — resets on the 1st of each month (salary, groceries, fuel)
     * FIXED   — never resets automatically (rent, EMI, loan)
     */
    private ResetType resetType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum ResetType {
        MONTHLY,
        FIXED
    }
}