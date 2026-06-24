package com.coreissuer.api.exception;

public class CardNotFoundException extends RuntimeException {
    public CardNotFoundException(String id) {
        super("Card not found: id=" + id);
    }
}
