package com.coreissuer.api.controller;

import com.coreissuer.api.dto.AuthorizeRequest;
import com.coreissuer.api.dto.AuthorizeResponse;
import com.coreissuer.api.idempotency.IdempotencyService;
import com.coreissuer.api.service.TransactionService;
import com.coreissuer.common.util.CryptoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizeRequest request) {

        String requestHash;
        try {
            requestHash = CryptoUtils.simpleHash(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            log.error("Failed to hash authorize request for idempotency key={}", idempotencyKey, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "internal_server_error"));
        }

        try {
            Optional<String> cachedResponse = idempotencyService.getCachedResponse(idempotencyKey, requestHash);
            if (cachedResponse.isPresent()) {
                AuthorizeResponse response = objectMapper.readValue(cachedResponse.get(), AuthorizeResponse.class);
                return ResponseEntity.ok(response);
            }

            AuthorizeResponse response = transactionService.authorize(request);

            String responseBody = objectMapper.writeValueAsString(response);
            idempotencyService.saveResponse(idempotencyKey, "/api/v1/transactions/authorize", requestHash, 200, responseBody);

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "idempotency_conflict", "detail", e.getMessage()));
        } catch (Exception e) {
            log.error("Authorize failed for idempotency key={}", idempotencyKey, e);
            idempotencyService.clearProcessing(idempotencyKey);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "internal_server_error"));
        }
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<Void> capture(@PathVariable String id) {
        transactionService.capture(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<Void> reverse(@PathVariable String id) {
        transactionService.reverse(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<Void> refund(@PathVariable String id) {
        transactionService.refund(id);
        return ResponseEntity.ok().build();
    }
}
