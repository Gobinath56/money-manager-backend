package com.moneymanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")          // MongoDB collection name
public class User {

    @Id
    private String id;

    @Indexed(unique = true)              // MongoDB unique index on email
    private String email;

    private String password;             // stored as BCrypt hash, never plaintext
}