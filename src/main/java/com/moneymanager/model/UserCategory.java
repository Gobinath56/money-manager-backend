package com.moneymanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_categories")
public class UserCategory {

    @Id
    private String id;

    private String userId;           // owner

    private String name;             // e.g. "TRIP"

    private Transaction.TransactionType type; // INCOME or EXPENSE

    private List<String> subCategories; // ["Travel", "Hotel", "Food", "Activities"]

    private boolean isCustom;        // true = user created, false = system default
}