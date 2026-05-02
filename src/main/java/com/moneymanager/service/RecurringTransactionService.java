package com.moneymanager.service;

import com.moneymanager.dto.RecurringTransactionRequest;
import com.moneymanager.dto.TransactionRequest;
import com.moneymanager.exception.ResourceNotFoundException;
import com.moneymanager.model.RecurringTransaction;
import com.moneymanager.model.RecurringTransaction.Frequency;
import com.moneymanager.model.Transaction;
import com.moneymanager.repository.RecurringTransactionRepository;
import com.moneymanager.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j                   // gives us log.info(), log.error() etc. via Lombok
@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringRepo;
    private final TransactionService transactionService;
    private final SecurityUtils securityUtils;

    // ══════════════════════════════════════════
    //  CREATE
    // ══════════════════════════════════════════

    public RecurringTransaction create(RecurringTransactionRequest req) {
        RecurringTransaction r = new RecurringTransaction();

        r.setUserId(securityUtils.getCurrentUserEmail());
        r.setDescription(req.getDescription());
        r.setAmount(req.getAmount());
        r.setType(req.getType());
        r.setCategory(req.getCategory());
        r.setDivision(req.getDivision());
        r.setFrequency(req.getFrequency());
        r.setNextRunDate(req.getStartDate());  // first run = startDate
        r.setCreatedAt(LocalDateTime.now());
        r.setActive(true);

        return recurringRepo.save(r);
    }

    // ══════════════════════════════════════════
    //  GET ALL for current user
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

        r.setActive(!r.isActive());   // flip the boolean
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
    //  RUN ONE MANUALLY (from frontend button)
    // ══════════════════════════════════════════

    public void runNow(String id) {
        String userId = securityUtils.getCurrentUserEmail();
        RecurringTransaction r = recurringRepo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));

        createTransactionFrom(r);         // create the actual transaction
        r.setLastRunDate(LocalDateTime.now());
        r.setNextRunDate(calculateNext(r.getFrequency(), LocalDateTime.now()));
        recurringRepo.save(r);
    }

    // ══════════════════════════════════════════
    //  @Scheduled — THE AUTO-RUN LOGIC
    //
    //  cron = "0 0 0 * * *"
    //    ┌─ second (0)
    //    │  ┌─ minute (0)
    //    │  │  ┌─ hour (0 = midnight)
    //    │  │  │  ┌─ day of month (* = every day)
    //    │  │  │  │  ┌─ month (* = every month)
    //    │  │  │  │  │  ┌─ day of week (* = every day)
    //    0  0  0  *  *  *
    //
    //  This runs automatically at midnight every day.
    //  No HTTP request needed — Spring calls it internally.
    //  The server must be running for this to fire.
    // ══════════════════════════════════════════

    @Scheduled(cron = "0 0 0 * * *")
    public void processAllDue() {
        LocalDateTime now = LocalDateTime.now();

        // Find ALL active recurring transactions across ALL users
        // whose nextRunDate has passed
        List<RecurringTransaction> due =
                recurringRepo.findByActiveIsTrueAndNextRunDateLessThanEqual(now);

        log.info("Recurring scheduler: found {} due transactions", due.size());

        for (RecurringTransaction r : due) {
            try {
                createTransactionFrom(r);

                // Advance nextRunDate so it doesn't fire again until next period
                r.setLastRunDate(now);
                r.setNextRunDate(calculateNext(r.getFrequency(), now));
                recurringRepo.save(r);

                log.info("Created recurring transaction: {} for user {}",
                        r.getDescription(), r.getUserId());

            } catch (Exception e) {
                // Log and continue — one failure shouldn't stop others from running
                log.error("Failed to process recurring transaction {}: {}",
                        r.getId(), e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════
    //  PRIVATE HELPERS
    // ══════════════════════════════════════════

    /**
     * Creates an actual Transaction from a RecurringTransaction definition.
     *
     * KEY POINT: we call transactionRepository directly here instead of
     * transactionService.createTransaction() because that method reads the
     * current user from SecurityContext — but @Scheduled runs with NO
     * HTTP request and NO security context. So we set userId manually.
     */
    private void createTransactionFrom(RecurringTransaction r) {
        TransactionRequest req = new TransactionRequest();
        req.setType(r.getType());
        req.setAmount(r.getAmount());
        req.setDescription(r.getDescription() + " (auto)");
        req.setCategory(r.getCategory());
        req.setDivision(r.getDivision());
        req.setDate(LocalDateTime.now());

        // Call the internal method — bypasses SecurityContext check
        transactionService.createTransactionInternal(r.getUserId(), req);
    }

    /**
     * Calculates the next run date based on frequency.
     * LocalDateTime.plusDays/plusMonths etc. handles month-length differences.
     * e.g. Jan 31 + 1 month = Feb 28 (not March 2)
     */
    private LocalDateTime calculateNext(Frequency frequency, LocalDateTime from) {
        return switch (frequency) {
            case DAILY   -> from.plusDays(1);
            case WEEKLY  -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case YEARLY  -> from.plusYears(1);
        };
        // switch expression (Java 14+) — no break needed, returns the value directly
    }
}