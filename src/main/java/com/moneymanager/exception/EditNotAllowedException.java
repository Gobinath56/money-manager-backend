package com.moneymanager.exception;

public class EditNotAllowedException extends RuntimeException {
    public EditNotAllowedException(String message) {
        super(message);
    }
}
