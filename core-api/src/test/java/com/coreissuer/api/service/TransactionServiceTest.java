package com.coreissuer.api.service;

import com.coreissuer.api.dto.AuthorizeRequest;
import com.coreissuer.api.dto.AuthorizeResponse;
import com.coreissuer.api.exception.CardNotFoundException;
import com.coreissuer.api.exception.TransactionNotFoundException;
import com.coreissuer.api.fraud.FraudCheck;
import com.coreissuer.api.fraud.FraudResult;
import com.coreissuer.api.strategy.AuthorizationStrategy;
import com.coreissuer.common.domain.*;
import com.coreissuer.common.repository.AccountRepository;
import com.coreissuer.common.repository.CardRepository;
import com.coreissuer.common.repository.LedgerEntryRepository;
import com.coreissuer.common.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private CardRepository cardRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private TransactionService service;

    private final AuthorizationStrategy passThroughStrategy = new AuthorizationStrategy() {
        public boolean supports(String c, String m) { return true; }
        public BigDecimal computeFee(BigDecimal a) { return BigDecimal.ZERO; }
        public String checkRules(BigDecimal a) { return null; }
    };
    private final FraudCheck passFraudCheck = req -> FraudResult.pass();

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                cardRepository, accountRepository, transactionRepository,
                ledgerEntryRepository, Collections.singletonList(passThroughStrategy),
                Collections.singletonList(passFraudCheck), eventPublisher, new ObjectMapper()
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Card activeCard(String cardId, String accountId) {
        Account account = new Account();
        account.setId(accountId);
        account.setCurrency("USD");
        account.setAvailableBalance(new BigDecimal("500.00"));
        account.setLedgerBalance(new BigDecimal("500.00"));

        Card card = new Card();
        card.setId(cardId);
        card.setStatus(CardStatus.ACTIVE.name());
        card.setAccount(account);
        return card;
    }

    private Account networkAccount() {
        Account a = new Account();
        a.setId("acc-network-settle");
        a.setAvailableBalance(BigDecimal.ZERO);
        a.setLedgerBalance(BigDecimal.ZERO);
        return a;
    }

    private Account merchantAccount() {
        Account a = new Account();
        a.setId("acc-merchant-1");
        a.setAvailableBalance(new BigDecimal("100.00"));
        a.setLedgerBalance(new BigDecimal("100.00"));
        return a;
    }

    private AuthorizeRequest request(String cardId, String amount) {
        return AuthorizeRequest.builder()
                .cardId(cardId).merchantId("1").mcc("5411")
                .amount(new BigDecimal(amount)).currency("USD")
                .build();
    }

    private Transaction savedTxn(String id, String status, BigDecimal amount, Card card) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setStatus(status);
        t.setAmount(amount);
        t.setMerchantId("1");
        t.setCard(card);
        return t;
    }

    /** Stubs transactionRepository.save() to assign an ID and return the same object. */
    private void stubTransactionSave(String id) {
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            if (t.getId() == null) t.setId(id);
            return t;
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // authorize
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("authorize")
    class Authorize {

        @Test
        @DisplayName("succeeds: balance debited and ledger pair written")
        void success_deductsBalanceAndWritesLedger() {
            Card card = activeCard("card-1", "acc-ch-1");
            Account network = networkAccount();
            stubTransactionSave("txn-1");
            when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(accountRepository.findByIdForUpdate("acc-network-settle")).thenReturn(Optional.of(network));

            AuthorizeResponse resp = service.authorize(request("card-1", "42.00"));

            assertThat(resp.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED.name());
            assertThat(resp.getAmount()).isEqualByComparingTo("42.00");
            assertThat(resp.getAvailableBalanceAfter()).isEqualByComparingTo("458.00");
            verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("ledger debit is on cardholder, credit is on network settlement")
        void success_ledgerDirectionsCorrect() {
            Card card = activeCard("card-1", "acc-ch-1");
            Account network = networkAccount();
            stubTransactionSave("txn-1");
            when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(accountRepository.findByIdForUpdate("acc-network-settle")).thenReturn(Optional.of(network));

            service.authorize(request("card-1", "42.00"));

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerEntryRepository, times(2)).save(captor.capture());
            List<LedgerEntry> entries = captor.getAllValues();

            LedgerEntry debit  = entries.stream().filter(e -> "D".equals(e.getDirection())).findFirst()
                    .orElseThrow(() -> new AssertionError("no debit entry"));
            LedgerEntry credit = entries.stream().filter(e -> "C".equals(e.getDirection())).findFirst()
                    .orElseThrow(() -> new AssertionError("no credit entry"));

            assertThat(debit.getAccount().getId()).isEqualTo("acc-ch-1");
            assertThat(credit.getAccount().getId()).isEqualTo("acc-network-settle");
            assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount());
        }

        @Test
        @DisplayName("declines with INSUFFICIENT_FUNDS when balance too low")
        void insufficientFunds_declines() {
            Card card = activeCard("card-1", "acc-ch-1");
            card.getAccount().setAvailableBalance(new BigDecimal("10.00"));
            stubTransactionSave("txn-d");
            when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));

            AuthorizeResponse resp = service.authorize(request("card-1", "42.00"));

            assertThat(resp.getStatus()).isEqualTo(TransactionStatus.DECLINED.name());
            assertThat(resp.getDeclineReason()).isEqualTo("INSUFFICIENT_FUNDS");
            verify(ledgerEntryRepository, never()).save(any());
        }

        @Test
        @DisplayName("declines with CARD_NOT_ACTIVE when card is frozen")
        void frozenCard_declines() {
            Card card = activeCard("card-1", "acc-ch-1");
            card.setStatus(CardStatus.FROZEN.name());
            stubTransactionSave("txn-d");
            when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

            AuthorizeResponse resp = service.authorize(request("card-1", "10.00"));

            assertThat(resp.getStatus()).isEqualTo(TransactionStatus.DECLINED.name());
            assertThat(resp.getDeclineReason()).isEqualTo("CARD_NOT_ACTIVE");
        }

        @Test
        @DisplayName("declines when a fraud check blocks the request")
        void fraudBlocked_declines() {
            Card card = activeCard("card-1", "acc-ch-1");
            stubTransactionSave("txn-d");
            when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));

            TransactionService svc = new TransactionService(
                    cardRepository, accountRepository, transactionRepository,
                    ledgerEntryRepository, Collections.singletonList(passThroughStrategy),
                    Collections.singletonList(req -> FraudResult.block("VELOCITY_LIMIT_EXCEEDED")),
                    eventPublisher, new ObjectMapper()
            );

            AuthorizeResponse resp = svc.authorize(request("card-1", "10.00"));

            assertThat(resp.getStatus()).isEqualTo(TransactionStatus.DECLINED.name());
            assertThat(resp.getDeclineReason()).isEqualTo("VELOCITY_LIMIT_EXCEEDED");
        }

        @Test
        @DisplayName("declines with CURRENCY_MISMATCH when request currency differs from account currency")
        void currencyMismatch_declines() {
            Card card = activeCard("card-1", "acc-ch-1"); // account currency is USD
            stubTransactionSave("txn-d");
            when(cardRepository.findById("card-1")).thenReturn(Optional.of(card));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));

            AuthorizeRequest req = AuthorizeRequest.builder()
                    .cardId("card-1").merchantId("1").mcc("5411")
                    .amount(new BigDecimal("10.00")).currency("EUR")
                    .build();

            AuthorizeResponse resp = service.authorize(req);

            assertThat(resp.getStatus()).isEqualTo(TransactionStatus.DECLINED.name());
            assertThat(resp.getDeclineReason()).isEqualTo("CURRENCY_MISMATCH");
            verify(ledgerEntryRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws CardNotFoundException for an unknown card id")
        void unknownCard_throwsCardNotFound() {
            when(cardRepository.findById("bad-card")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.authorize(request("bad-card", "10.00")))
                    .isInstanceOf(CardNotFoundException.class)
                    .hasMessageContaining("bad-card");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // capture
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("capture")
    class Capture {

        @Test
        @DisplayName("moves funds from network to merchant and writes ledger pair")
        void authorized_movesBalanceAndWritesLedger() {
            Card card = activeCard("card-1", "acc-ch-1");
            Transaction txn = savedTxn("txn-1", TransactionStatus.AUTHORIZED.name(), new BigDecimal("42.00"), card);
            Account network = networkAccount();
            network.setLedgerBalance(new BigDecimal("42.00"));
            Account merchant = merchantAccount();

            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));
            when(accountRepository.findByIdForUpdate("acc-network-settle")).thenReturn(Optional.of(network));
            when(accountRepository.findByIdForUpdate("acc-merchant-1")).thenReturn(Optional.of(merchant));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(transactionRepository.save(any())).thenReturn(txn);

            service.capture("txn-1");

            assertThat(merchant.getAvailableBalance()).isEqualByComparingTo("142.00");
            assertThat(merchant.getLedgerBalance()).isEqualByComparingTo("142.00");
            assertThat(network.getLedgerBalance()).isEqualByComparingTo("0.00");
            // hold reduced available at authorize; posting reduces the ledger balance
            assertThat(card.getAccount().getLedgerBalance()).isEqualByComparingTo("458.00");
            verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("posts the fee to the fee revenue account and writes a second ledger pair")
        void authorizedWithFee_postsFeeToFeeRevenue() {
            Card card = activeCard("card-1", "acc-ch-1");
            Transaction txn = savedTxn("txn-1", TransactionStatus.AUTHORIZED.name(), new BigDecimal("40.00"), card);
            txn.setFeeAmount(new BigDecimal("1.00"));
            Account network = networkAccount();
            network.setLedgerBalance(new BigDecimal("41.00"));
            Account merchant = merchantAccount();
            Account feeRevenue = new Account();
            feeRevenue.setId("acc-fee-revenue");
            feeRevenue.setAvailableBalance(BigDecimal.ZERO);
            feeRevenue.setLedgerBalance(BigDecimal.ZERO);

            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));
            when(accountRepository.findByIdForUpdate("acc-network-settle")).thenReturn(Optional.of(network));
            when(accountRepository.findByIdForUpdate("acc-merchant-1")).thenReturn(Optional.of(merchant));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(accountRepository.findByIdForUpdate("acc-fee-revenue")).thenReturn(Optional.of(feeRevenue));
            when(transactionRepository.save(any())).thenReturn(txn);

            service.capture("txn-1");

            assertThat(feeRevenue.getAvailableBalance()).isEqualByComparingTo("1.00");
            assertThat(feeRevenue.getLedgerBalance()).isEqualByComparingTo("1.00");
            assertThat(merchant.getAvailableBalance()).isEqualByComparingTo("140.00");
            assertThat(network.getLedgerBalance()).isEqualByComparingTo("0.00");
            assertThat(card.getAccount().getLedgerBalance()).isEqualByComparingTo("459.00");
            verify(ledgerEntryRepository, times(4)).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("ledger debit is on network, credit is on merchant")
        void authorized_ledgerDirectionsCorrect() {
            Card card = activeCard("card-1", "acc-ch-1");
            Transaction txn = savedTxn("txn-1", TransactionStatus.AUTHORIZED.name(), new BigDecimal("42.00"), card);
            Account network = networkAccount();
            network.setLedgerBalance(new BigDecimal("42.00"));
            Account merchant = merchantAccount();

            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));
            when(accountRepository.findByIdForUpdate("acc-network-settle")).thenReturn(Optional.of(network));
            when(accountRepository.findByIdForUpdate("acc-merchant-1")).thenReturn(Optional.of(merchant));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(transactionRepository.save(any())).thenReturn(txn);

            service.capture("txn-1");

            ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
            verify(ledgerEntryRepository, times(2)).save(captor.capture());
            List<LedgerEntry> entries = captor.getAllValues();

            LedgerEntry debit  = entries.stream().filter(e -> "D".equals(e.getDirection())).findFirst()
                    .orElseThrow(() -> new AssertionError("no debit entry"));
            LedgerEntry credit = entries.stream().filter(e -> "C".equals(e.getDirection())).findFirst()
                    .orElseThrow(() -> new AssertionError("no credit entry"));

            assertThat(debit.getAccount().getId()).isEqualTo("acc-network-settle");
            assertThat(credit.getAccount().getId()).isEqualTo("acc-merchant-1");
        }

        @Test
        @DisplayName("throws TransactionNotFoundException for unknown transaction id")
        void unknownTransaction_throws() {
            when(transactionRepository.findById("bad")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.capture("bad"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("bad");
        }

        @Test
        @DisplayName("throws IllegalStateException when trying to capture an already-captured transaction")
        void alreadyCaptured_throwsIllegalState() {
            Card card = activeCard("card-1", "acc-ch-1");
            Transaction txn = savedTxn("txn-1", TransactionStatus.CAPTURED.name(), new BigDecimal("10.00"), card);
            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));

            assertThatThrownBy(() -> service.capture("txn-1"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // reverse
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("reverse")
    class Reverse {

        @Test
        @DisplayName("releases hold back to cardholder and writes ledger pair")
        void authorized_restoresCardholderBalance() {
            Card card = activeCard("card-1", "acc-ch-1");
            card.getAccount().setAvailableBalance(new BigDecimal("458.00"));
            Transaction txn = savedTxn("txn-1", TransactionStatus.AUTHORIZED.name(), new BigDecimal("42.00"), card);
            Account network = networkAccount();
            network.setLedgerBalance(new BigDecimal("42.00"));

            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));
            when(accountRepository.findByIdForUpdate("acc-network-settle")).thenReturn(Optional.of(network));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(transactionRepository.save(any())).thenReturn(txn);

            service.reverse("txn-1");

            assertThat(card.getAccount().getAvailableBalance()).isEqualByComparingTo("500.00");
            assertThat(network.getLedgerBalance()).isEqualByComparingTo("0.00");
            verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("releases the fee together with the amount")
        void authorizedWithFee_releasesFullHold() {
            Card card = activeCard("card-1", "acc-ch-1");
            card.getAccount().setAvailableBalance(new BigDecimal("459.00")); // 500 - (40 + 1 fee)
            Transaction txn = savedTxn("txn-1", TransactionStatus.AUTHORIZED.name(), new BigDecimal("40.00"), card);
            txn.setFeeAmount(new BigDecimal("1.00"));
            Account network = networkAccount();
            network.setLedgerBalance(new BigDecimal("41.00"));

            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));
            when(accountRepository.findByIdForUpdate("acc-network-settle")).thenReturn(Optional.of(network));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(transactionRepository.save(any())).thenReturn(txn);

            service.reverse("txn-1");

            assertThat(card.getAccount().getAvailableBalance()).isEqualByComparingTo("500.00");
            assertThat(network.getLedgerBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("throws TransactionNotFoundException for unknown transaction id")
        void unknownTransaction_throws() {
            when(transactionRepository.findById("bad")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reverse("bad"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("bad");
        }

        @Test
        @DisplayName("throws IllegalStateException when reversing an already-reversed transaction")
        void alreadyReversed_throwsIllegalState() {
            Card card = activeCard("card-1", "acc-ch-1");
            Transaction txn = savedTxn("txn-1", TransactionStatus.REVERSED.name(), new BigDecimal("10.00"), card);
            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));

            assertThatThrownBy(() -> service.reverse("txn-1"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // refund
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("refund")
    class Refund {

        @Test
        @DisplayName("moves funds from merchant back to cardholder and writes ledger pair")
        void captured_movesBalanceBackToCardholder() {
            Card card = activeCard("card-1", "acc-ch-1");
            Transaction txn = savedTxn("txn-1", TransactionStatus.CAPTURED.name(), new BigDecimal("42.00"), card);
            Account merchant = merchantAccount();

            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));
            when(accountRepository.findByIdForUpdate("acc-merchant-1")).thenReturn(Optional.of(merchant));
            when(accountRepository.findByIdForUpdate("acc-ch-1")).thenReturn(Optional.of(card.getAccount()));
            when(transactionRepository.save(any())).thenReturn(txn);

            service.refund("txn-1");

            assertThat(card.getAccount().getAvailableBalance()).isEqualByComparingTo("542.00");
            assertThat(merchant.getAvailableBalance()).isEqualByComparingTo("58.00");
            assertThat(merchant.getLedgerBalance()).isEqualByComparingTo("58.00");
            verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));
        }

        @Test
        @DisplayName("throws TransactionNotFoundException for unknown transaction id")
        void unknownTransaction_throws() {
            when(transactionRepository.findById("bad")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refund("bad"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("bad");
        }

        @Test
        @DisplayName("throws IllegalStateException when refunding an AUTHORIZED (not yet captured) transaction")
        void authorizedNotCaptured_throwsIllegalState() {
            Card card = activeCard("card-1", "acc-ch-1");
            // AUTHORIZED → REFUNDED is not a valid transition
            Transaction txn = savedTxn("txn-1", TransactionStatus.AUTHORIZED.name(), new BigDecimal("10.00"), card);
            when(transactionRepository.findById("txn-1")).thenReturn(Optional.of(txn));

            assertThatThrownBy(() -> service.refund("txn-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AUTHORIZED")
                    .hasMessageContaining("REFUNDED");
        }
    }
}
