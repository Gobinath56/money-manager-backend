package com.moneymanager.service;

import com.moneymanager.dto.UserCategoryRequest;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.Transaction.TransactionType;
import com.moneymanager.model.UserCategory;
import com.moneymanager.repository.UserCategoryRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserCategoryService {

    private final UserCategoryRepository categoryRepository;
    private final SecurityUtils securityUtils;

    // ── Default subcategories seeded for every user ───────────────────────
    // Called after first login — gives users a useful starting point
    private static final List<UserCategory> DEFAULTS = List.of(
            makeDefault("FOOD",       TransactionType.EXPENSE, List.of("Breakfast","Lunch","Dinner","Snacks","Groceries","Restaurant")),
            makeDefault("FUEL",       TransactionType.EXPENSE, List.of("Petrol","Diesel","EV Charge","CNG")),
            makeDefault("TRIP",       TransactionType.EXPENSE, List.of("Travel","Hotel","Food","Activities","Shopping","Transport")),
            makeDefault("MEDICAL",    TransactionType.EXPENSE, List.of("Medicine","Doctor","Lab Test","Hospital","Insurance")),
            makeDefault("MOVIE",      TransactionType.EXPENSE, List.of("Cinema","OTT Subscription","Events","Concerts")),
            makeDefault("LOAN",       TransactionType.EXPENSE, List.of("EMI","Interest","Credit Card")),
            makeDefault("OTHER",      TransactionType.EXPENSE, List.of()),
            makeDefault("SALARY",     TransactionType.INCOME,  List.of("Basic Pay","Bonus","Allowance","Overtime")),
            makeDefault("FREELANCE",  TransactionType.INCOME,  List.of("Project","Consultation","Contract")),
            makeDefault("INVESTMENT", TransactionType.INCOME,  List.of("Dividend","Interest","Capital Gains","Returns")),
            makeDefault("OTHER",      TransactionType.INCOME,  List.of())
    );

    private static UserCategory makeDefault(String name, TransactionType type, List<String> subs) {
        UserCategory c = new UserCategory();
        c.setName(name);
        c.setType(type);
        c.setSubCategories(new ArrayList<>(subs));
        c.setCustom(false);
        return c;
    }

    // ── Seed defaults for a new user ──────────────────────────────────────
    // Call this from AuthService.register() after saving the user
    public void seedDefaultsForUser(String userId) {
        // Only seed if user has no categories yet
        if (!categoryRepository.findByUserId(userId).isEmpty()) return;

        DEFAULTS.forEach(template -> {
            UserCategory c = new UserCategory();
            c.setUserId(userId);
            c.setName(template.getName());
            c.setType(template.getType());
            c.setSubCategories(new ArrayList<>(template.getSubCategories()));
            c.setCustom(false);
            categoryRepository.save(c);
        });
    }

    // ── Get all categories for current user ───────────────────────────────
    public List<UserCategory> getAllForCurrentUser() {
        return categoryRepository.findByUserId(securityUtils.getCurrentUserEmail());
    }

    // ── Get by type ───────────────────────────────────────────────────────
    public List<UserCategory> getByType(TransactionType type) {
        return categoryRepository.findByUserIdAndType(
                securityUtils.getCurrentUserEmail(), type);
    }

    // ── Create custom category ────────────────────────────────────────────
    public UserCategory create(UserCategoryRequest request) {
        String userId = securityUtils.getCurrentUserEmail();

        // Check duplicate name for this type
        categoryRepository.findByUserIdAndName(userId, request.getName().toUpperCase())
                .ifPresent(c -> { throw new RuntimeException("Category already exists"); });

        UserCategory category = new UserCategory();
        category.setUserId(userId);
        category.setName(request.getName().toUpperCase().trim());
        category.setType(request.getType());
        category.setSubCategories(
                request.getSubCategories() != null
                        ? request.getSubCategories()
                        : new ArrayList<>()
        );
        category.setCustom(true);

        return categoryRepository.save(category);
    }

    // ── Add subcategory to existing category ──────────────────────────────
    public UserCategory addSubCategory(String categoryId, String subCategoryName) {
        String userId = securityUtils.getCurrentUserEmail();
        UserCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.getSubCategories().contains(subCategoryName)) {
            category.getSubCategories().add(subCategoryName.trim());
            categoryRepository.save(category);
        }
        return category;
    }

    // ── Remove subcategory ────────────────────────────────────────────────
    public UserCategory removeSubCategory(String categoryId, String subCategoryName) {
        String userId = securityUtils.getCurrentUserEmail();
        UserCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        category.getSubCategories().remove(subCategoryName);
        return categoryRepository.save(category);
    }

    // ── Delete custom category ────────────────────────────────────────────
    // Only custom categories can be deleted — defaults are protected
    public void delete(String categoryId) {
        String userId = securityUtils.getCurrentUserEmail();
        UserCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (!category.isCustom()) {
            throw new RuntimeException("Default categories cannot be deleted");
        }
        categoryRepository.delete(category);
    }
}