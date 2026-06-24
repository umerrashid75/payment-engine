package com.coreissuer.api.exception;

public class AuthorizationStrategyNotFoundException extends RuntimeException {
    public AuthorizationStrategyNotFoundException(String currency, String country) {
        super("No authorization strategy for currency=" + currency + " country=" + country);
    }
}
