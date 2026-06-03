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
    public void seedDefaultsForUser(String userId) {
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

        // FIX #2 — Sanitize category name before saving.
        //
        // THE BUG:
        //   Only .toUpperCase().trim() was applied. A user could save
        //   "<script>alert('xss')</script>" as a category name. It would
        //   be stored in MongoDB and rendered on every frontend page that
        //   displays categories — affecting all the user's devices.
        //
        // THE FIX:
        //   Strip everything except A-Z, 0-9, underscore, and space.
        //   Then check the result is not empty before proceeding.
        //   Examples:
        //     "My Food"               → "MY FOOD"         ✓ allowed
        //     "TRIP_2024"             → "TRIP_2024"        ✓ allowed
        //     "<script>alert</script>"→ "SCRIPTALERTSCRIPT" → valid but harmless
        //     "!!!###"                → ""                 → rejected below
        String name = request.getName()
                .toUpperCase()
                .trim()
                .replaceAll("[^A-Z0-9_ ]", ""); // strip all special characters

        // Reject if sanitization left an empty string
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Category name contains invalid characters. " +
                            "Only letters, numbers, spaces and underscores are allowed."
            );
        }

        // Reject if longer than 30 characters (prevent DB bloat / UI overflow)
        if (name.length() > 30) {
            throw new IllegalArgumentException(
                    "Category name must be 30 characters or fewer."
            );
        }

        // Check duplicate name for this user
        categoryRepository.findByUserIdAndName(userId, name)
                .ifPresent(c -> {
                    throw new RuntimeException("Category '" + name + "' already exists");
                });

        // Sanitize each subcategory name the same way
        List<String> cleanSubs = new ArrayList<>();
        if (request.getSubCategories() != null) {
            for (String sub : request.getSubCategories()) {
                // Subcategories allow mixed case and more characters (they are
                // display labels, not enum-style keys) — but still strip HTML
                String cleanSub = sub.trim().replaceAll("[<>\"'&;]", "");
                if (!cleanSub.isEmpty() && cleanSub.length() <= 50) {
                    cleanSubs.add(cleanSub);
                }
            }
        }

        UserCategory category = new UserCategory();
        category.setUserId(userId);
        category.setName(name);
        category.setType(request.getType());
        category.setSubCategories(cleanSubs);
        category.setCustom(true);

        return categoryRepository.save(category);
    }

    // ── Add subcategory to existing category ──────────────────────────────
    public UserCategory addSubCategory(String categoryId, String subCategoryName) {
        String userId = securityUtils.getCurrentUserEmail();
        UserCategory category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        // FIX #2 — sanitize subcategory names too
        String cleanSub = subCategoryName.trim().replaceAll("[<>\"'&;]", "");
        if (cleanSub.isEmpty()) {
            throw new IllegalArgumentException("Subcategory name contains invalid characters.");
        }
        if (cleanSub.length() > 50) {
            throw new IllegalArgumentException("Subcategory name must be 50 characters or fewer.");
        }

        if (!category.getSubCategories().contains(cleanSub)) {
            category.getSubCategories().add(cleanSub);
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