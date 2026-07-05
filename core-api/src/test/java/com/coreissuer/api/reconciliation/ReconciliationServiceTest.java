package com.coreissuer.api.reconciliation;

import com.coreissuer.common.domain.Account;
import com.coreissuer.common.domain.LedgerEntry;
import com.coreissuer.common.domain.Transaction;
import com.coreissuer.common.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(ledgerEntryRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private LedgerEntry entry(String txnId, String accountId, String direction, String amount) {
        Transaction txn = new Transaction();
        txn.setId(txnId);

        Account account = new Account();
        account.setId(accountId);

        LedgerEntry e = new LedgerEntry();
        e.setTransaction(txn);
        e.setAccount(account);
        e.setDirection(direction);
        e.setAmount(new BigDecimal(amount));
        e.setPostedAt(LocalDateTime.now());
        return e;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // double-entry integrity checks
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("double-entry integrity")
    class DoubleEntryIntegrity {

        @Test
        @DisplayName("no discrepancies when every transaction has balanced debit and credit")
        void balanced_noDiscrepancies() {
            when(ledgerEntryRepository.findAll()).thenReturn(Arrays.asList(
                    entry("txn-1", "acc-cardholder", "D", "42.00"),
                    entry("txn-1", "acc-network-settle", "C", "42.00"),
                    entry("txn-2", "acc-cardholder", "D", "10.00"),
                    entry("txn-2", "acc-network-settle", "C", "10.00")
            ));

            String report = service.reconcile();

            assertThat(report).contains("Total Discrepancies: 0");
            assertThat(report).doesNotContain("DISCREPANCY DETECTED");
        }

        @Test
        @DisplayName("flags transaction where debits and credits differ")
        void imbalanced_reportsDiscrepancy() {
            when(ledgerEntryRepository.findAll()).thenReturn(Arrays.asList(
                    entry("txn-bad", "acc-cardholder", "D", "42.00"),
                    entry("txn-bad", "acc-network-settle", "C", "40.00")
            ));

            String report = service.reconcile();

            assertThat(report).contains("DISCREPANCY DETECTED");
            assertThat(report).contains("txn-bad");
            assertThat(report).contains("Total Discrepancies: 1");
        }

        @Test
        @DisplayName("counts each imbalanced transaction separately")
        void multipleDiscrepancies_allCounted() {
            when(ledgerEntryRepository.findAll()).thenReturn(Arrays.asList(
                    entry("txn-a", "acc-ch", "D", "50.00"),
                    entry("txn-a", "acc-net", "C", "40.00"),  // $10 gap
                    entry("txn-b", "acc-ch", "D", "30.00"),
                    entry("txn-b", "acc-net", "C", "20.00"),  // $10 gap
                    entry("txn-ok", "acc-ch", "D", "15.00"),
                    entry("txn-ok", "acc-net", "C", "15.00")  // balanced
            ));

            String report = service.reconcile();

            assertThat(report).contains("Total Discrepancies: 2");
            assertThat(report).contains("txn-a");
            assertThat(report).contains("txn-b");
            assertThat(report).doesNotContain("txn-ok");
        }

        @Test
        @DisplayName("empty ledger produces a valid report with zero discrepancies")
        void emptyLedger_zeroDiscrepancies() {
            when(ledgerEntryRepository.findAll()).thenReturn(Collections.emptyList());

            String report = service.reconcile();

            assertThat(report).contains("Total Discrepancies: 0");
        }

        @Test
        @DisplayName("report always contains the standard header line")
        void report_containsHeader() {
            when(ledgerEntryRepository.findAll()).thenReturn(Collections.emptyList());

            String report = service.reconcile();

            assertThat(report).contains("=== CoreIssuer Reconciliation Report ===");
            assertThat(report).contains("Generated at:");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // merchant settlement bucketing
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("merchant settlement bucketing")
    class MerchantBucketing {

        @Test
        @DisplayName("merchant volume appears in report bucketed by account id")
        void singleMerchant_appearsInReport() {
            when(ledgerEntryRepository.findAll()).thenReturn(Arrays.asList(
                    entry("txn-1", "acc-merchant-1", "C", "55.00"),
                    entry("txn-1", "acc-cardholder", "D", "55.00")
            ));

            String report = service.reconcile();

            assertThat(report).contains("acc-merchant-1");
            assertThat(report).contains("55.00");
        }

        @Test
        @DisplayName("multiple merchants each appear in their own section")
        void multipleMerchants_allBucketed() {
            when(ledgerEntryRepository.findAll()).thenReturn(Arrays.asList(
                    entry("txn-1", "acc-merchant-1", "C", "100.00"),
                    entry("txn-1", "acc-cardholder-a", "D", "100.00"),
                    entry("txn-2", "acc-merchant-2", "C", "200.00"),
                    entry("txn-2", "acc-cardholder-b", "D", "200.00")
            ));

            String report = service.reconcile();

            assertThat(report).contains("acc-merchant-1");
            assertThat(report).contains("acc-merchant-2");
        }

        @Test
        @DisplayName("net merchant volume is credits minus debits (refund reduces net)")
        void merchantNetVolume_creditsMinusDebits() {
            // Capture credit of $100, then refund debit of $30: net = $70
            when(ledgerEntryRepository.findAll()).thenReturn(Arrays.asList(
                    entry("txn-capture", "acc-merchant-1", "C", "100.00"),
                    entry("txn-capture", "acc-net", "D", "100.00"),
                    entry("txn-refund",  "acc-merchant-1", "D", "30.00"),
                    entry("txn-refund",  "acc-cardholder", "C", "30.00")
            ));

            String report = service.reconcile();

            // Net for merchant should be 100 - 30 = 70
            assertThat(report).contains("70.00");
        }
    }
}
