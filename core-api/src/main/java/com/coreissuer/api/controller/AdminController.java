package com.coreissuer.api.controller;

import com.coreissuer.api.reconciliation.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReconciliationService reconciliationService;

    @GetMapping("/ledger/reconcile")
    public ResponseEntity<String> reconcile() {
        String report = reconciliationService.reconcile();
        return ResponseEntity.ok(report);
    }
}
