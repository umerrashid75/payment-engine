package com.coreissuer.api.reconciliation;

import com.coreissuer.common.domain.LedgerEntry;
import com.coreissuer.common.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final LedgerEntryRepository ledgerEntryRepository;

    @Value("${coreissuer.reconciliation.report-dir:reports}")
    private String reportDir = "reports";

    @Scheduled(cron = "0 0 2 * * ?") // Run at 2 AM every day
    @Transactional(readOnly = true)
    public String reconcile() {
        List<LedgerEntry> allEntries = ledgerEntryRepository.findAll();
        
        // 1. Double-Entry Integrity Check
        Map<String, List<LedgerEntry>> entriesByTxn = allEntries.stream()
                .filter(e -> e.getTransaction() != null)
                .collect(Collectors.groupingBy(e -> e.getTransaction().getId()));

        int discrepancyCount = 0;
        StringBuilder reportBuilder = new StringBuilder();
        reportBuilder.append("=== CoreIssuer Reconciliation Report ===\n");
        reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append("\n\n");

        for (Map.Entry<String, List<LedgerEntry>> entry : entriesByTxn.entrySet()) {
            String txnId = entry.getKey();
            List<LedgerEntry> entries = entry.getValue();

            BigDecimal debitSum = entries.stream()
                    .filter(e -> "D".equals(e.getDirection()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal creditSum = entries.stream()
                    .filter(e -> "C".equals(e.getDirection()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (debitSum.compareTo(creditSum) != 0) {
                discrepancyCount++;
                reportBuilder.append("DISCREPANCY DETECTED: Txn ").append(txnId)
                        .append(" | Debits: ").append(debitSum)
                        .append(" | Credits: ").append(creditSum).append("\n");
            }
        }

        reportBuilder.append("Total Discrepancies: ").append(discrepancyCount).append("\n\n");

        // 2. Settlement Bucketing (Merchant Volume by Date)
        reportBuilder.append("=== Settlement Volume by Merchant (Captured/Refunded) ===\n");
        Map<String, List<LedgerEntry>> merchantEntries = allEntries.stream()
                .filter(e -> e.getAccount().getId().startsWith("acc-merchant"))
                .collect(Collectors.groupingBy(e -> e.getAccount().getId()));

        for (Map.Entry<String, List<LedgerEntry>> mEntry : merchantEntries.entrySet()) {
            String merchantId = mEntry.getKey();
            reportBuilder.append("Merchant: ").append(merchantId).append("\n");

            TreeMap<LocalDate, BigDecimal> volumeByDate = new TreeMap<>();
            for (LedgerEntry e : mEntry.getValue()) {
                LocalDate date = e.getPostedAt().toLocalDate();
                // Net volume: Credits minus Debits for merchant
                BigDecimal amount = "C".equals(e.getDirection()) ? e.getAmount() : e.getAmount().negate();
                volumeByDate.merge(date, amount, BigDecimal::add);
            }

            for (Map.Entry<LocalDate, BigDecimal> daily : volumeByDate.entrySet()) {
                reportBuilder.append("  ").append(daily.getKey()).append(": ").append(daily.getValue()).append("\n");
            }
        }

        String report = reportBuilder.toString();
        writeReportToFile(report);
        return report;
    }

    private void writeReportToFile(String report) {
        File dir = new File(reportDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, "recon-" + LocalDate.now() + ".txt");
        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            out.print(report);
        } catch (IOException e) {
            log.error("Failed to write reconciliation report to {}", file, e);
            throw new ReconciliationReportException("Failed to write reconciliation report", e);
        }
    }
}
