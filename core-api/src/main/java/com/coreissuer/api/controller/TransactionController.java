package com.coreissuer.api.controller;

import com.coreissuer.api.dto.AuthorizeRequest;
import com.coreissuer.api.dto.AuthorizeResponse;
import com.coreissuer.api.idempotency.IdempotencyService;
import com.coreissuer.api.service.TransactionService;
import com.coreissuer.common.util.CryptoUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {

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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to process request");
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
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            idempotencyService.clearProcessing(idempotencyKey);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<?> capture(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String id) {
        transactionService.capture(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<?> reverse(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String id) {
        transactionService.reverse(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<?> refund(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable String id) {
        transactionService.refund(id);
        return ResponseEntity.ok().build();
    }
}
