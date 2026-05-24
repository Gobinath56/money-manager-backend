package com.moneymanager.service;

import com.moneymanager.dto.RecurringTransactionRequest;
import com.moneymanager.dto.TransactionRequest;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.RecurringTransaction;
import com.moneymanager.model.RecurringTransaction.Frequency;
import com.moneymanager.repository.RecurringTransactionRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringRepo;
    private final TransactionService transactionService;
    private final SecurityUtils securityUtils;

    // ══════════════════════════════════════════
    //  CREATE
    //
    //  BUG FIX: accountId is now saved onto the RecurringTransaction document.
    //  Previously accountId was missing from the model and request DTO entirely,
    //  so it was impossible to link recurring transactions to any account.
    // ══════════════════════════════════════════

    public RecurringTransaction create(RecurringTransactionRequest req) {
        RecurringTransaction r = new RecurringTransaction();

        r.setUserId(securityUtils.getCurrentUserEmail());
        r.setDescription(req.getDescription());
        r.setAmount(req.getAmount());
        r.setType(req.getType());
        r.setCategory(req.getCategory());
        r.setDivision(req.getDivision());
        r.setAccountId(req.getAccountId()); // ← BUG FIX: was missing entirely
        r.setFrequency(req.getFrequency());
        r.setNextRunDate(req.getStartDate());
        r.setCreatedAt(LocalDateTime.now());
        r.setActive(true);

        return recurringRepo.save(r);
    }

    // ══════════════════════════════════════════
    //  GET ALL
    // ══════════════════════════════════════════

    public List<RecurringTransaction> getAllForCurrentUser() {
        return recurringRepo.findByUserId(securityUtils.getCurrentUserEmail());
    }

    // ══════════════════════════════════════════
    //  TOGGLE active/paused
    // ══════════════════════════════════════════

    public RecurringTransaction toggle(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        RecurringTransaction r = recurringRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring transaction not found"));

        r.setActive(!r.isActive());
        return recurringRepo.save(r);
    }

    // ══════════════════════════════════════════
    //  DELETE
    // ══════════════════════════════════════════

    public void delete(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        RecurringTransaction r = recurringRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recurring transaction not found"));
        recurringRepo.delete(r);
    }

    // ══════════════════════════════════════════
    //  RUN NOW  —  BUG FIX: was not updating account balance
    //
    //  THE BUG:
    //    runNow() called createTransactionFrom(r) which called
    //    createTransactionInternal() — a method that skipped balance updates
    //    (original version had no balance logic at all).
    //    Result: clicking "Run now" created a transaction record but left
    //    the account balance completely unchanged.
    //
    //  THE FIX:
    //    createTransactionInternal() now correctly updates the account balance
    //    (see TransactionService.java fix). No change needed here in runNow()
    //    itself — the fix is in the method it calls.
    //
    //    runNow() is called from an HTTP request where SecurityContext IS set,
    //    but we still use createTransactionInternal() because we need to pass
    //    userId directly (from the RecurringTransaction) rather than reading it
    //    from the context — this makes it consistent with the scheduler path.
    // ══════════════════════════════════════════

    public void runNow(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        RecurringTransaction r = recurringRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));

        createTransactionFrom(r);
        r.setLastRunDate(LocalDateTime.now());
        r.setNextRunDate(calculateNext(r.getFrequency(), LocalDateTime.now()));
        recurringRepo.save(r);
    }

    // ══════════════════════════════════════════
    //  SCHEDULER  —  runs at midnight every day
    // ══════════════════════════════════════════

    @Scheduled(cron = "0 0 0 * * *")
    public void processAllDue() {
        LocalDateTime now = LocalDateTime.now();

        List<RecurringTransaction> due =
                recurringRepo.findByActiveIsTrueAndNextRunDateLessThanEqual(now);

        log.info("Recurring scheduler: found {} due transactions", due.size());

        for (RecurringTransaction r : due) {
            try {
                createTransactionFrom(r);

                r.setLastRunDate(now);
                r.setNextRunDate(calculateNext(r.getFrequency(), now));
                recurringRepo.save(r);

                log.info("Created recurring transaction: {} for user {}",
                        r.getDescription(), r.getUserId());

            } catch (Exception e) {
                log.error("Failed to process recurring transaction {}: {}",
                        r.getId(), e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════

    /**
     * Builds a TransactionRequest from a RecurringTransaction and fires
     * createTransactionInternal(), which now correctly updates the account balance.
     *
     * KEY: r.getAccountId() is now populated (Bug Fix #3), so the
     * TransactionRequest carries a real accountId to the internal method.
     */
    private void createTransactionFrom(RecurringTransaction r) {
        TransactionRequest req = new TransactionRequest();
        req.setType(r.getType());
        req.setAmount(r.getAmount());
        req.setDescription(r.getDescription() + " (auto)");
        req.setCategory(r.getCategory());
        req.setDivision(r.getDivision());
        req.setAccountId(r.getAccountId()); // ← BUG FIX: was never set, always null
        req.setDate(LocalDateTime.now());

        // Bypasses SecurityContext — safe for both scheduler and runNow()
        transactionService.createTransactionInternal(r.getUserId(), req);
    }

    /**
     * Calculates the next run date based on frequency.
     */
    private LocalDateTime calculateNext(Frequency frequency, LocalDateTime from) {
        return switch (frequency) {
            case DAILY   -> from.plusDays(1);
            case WEEKLY  -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case YEARLY  -> from.plusYears(1);
        };
    }
}