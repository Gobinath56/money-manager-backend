package com.moneymanager.dto;

import lombok.Data;

@Data
public class TransferRequest {

    private String fromAccountId;
    private String toAccountId;
    private Double amount;
}
