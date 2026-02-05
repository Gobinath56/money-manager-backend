package com.moneymanager.dto;

import com.moneymanager.model.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    
    private Double totalIncome;
    private Double totalExpenditure;
    private Double balance;
    private List<Transaction> transactions;
    private Map<Transaction.Category, Double> categorySummary;

    // Summary data
    private SummaryData monthlySummary;
    private SummaryData weeklySummary;
    private SummaryData yearlySummary;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryData {
        private Double income;
        private Double expenditure;
        private Double balance;
    }
}
