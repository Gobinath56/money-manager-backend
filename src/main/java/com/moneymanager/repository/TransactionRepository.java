package com.moneymanager.repository;

import com.moneymanager.model.Transaction;
import com.moneymanager.model.Transaction.Category;
import com.moneymanager.model.Transaction.Division;
import com.moneymanager.model.Transaction.TransactionType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    
    // Find all transactions by type
    List<Transaction> findByType(TransactionType type);
    
    // Find transactions by division
    List<Transaction> findByDivision(Division division);
    
    // Find transactions by category
    List<Transaction> findByCategory(Category category);
    
    // Find transactions between dates
    List<Transaction> findByDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    // Find transactions by type and date range
    List<Transaction> findByTypeAndDateBetween(
        TransactionType type, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    );
    
    // Find transactions by division and date range
    List<Transaction> findByDivisionAndDateBetween(
        Division division, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    );
    
    // Find transactions by category and date range
    List<Transaction> findByCategoryAndDateBetween(
        Category category, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    );
    
    // Custom query for complex filtering
    @Query("{ 'type': ?0, 'division': ?1, 'date': { $gte: ?2, $lte: ?3 } }")
    List<Transaction> findByTypeAndDivisionAndDateBetween(
        TransactionType type,
        Division division,
        LocalDateTime startDate,
        LocalDateTime endDate
    );
}
