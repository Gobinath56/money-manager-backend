package com.moneymanager.repository;

import com.moneymanager.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);
    // Spring Data generates the query from the method name automatically.
    // "findBy" + "Email" → db.users.find({ email: value })

    boolean existsByEmail(String email);
    // Used to check duplicates during registration
}