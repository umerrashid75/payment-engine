package com.coreissuer.api.service;

import com.coreissuer.api.dto.AuthorizeRequest;
import com.coreissuer.api.dto.AuthorizeResponse;
import com.coreissuer.api.event.TransactionEvent;
import com.coreissuer.api.fraud.FraudCheck;
import com.coreissuer.api.fraud.FraudResult;
import com.coreissuer.api.strategy.AuthorizationStrategy;
import com.coreissuer.common.domain.*;
import com.coreissuer.common.repository.AccountRepository;
import com.coreissuer.common.repository.CardRepository;
import com.coreissuer.common.repository.LedgerEntryRepository;
import com.coreissuer.common.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final List<AuthorizationStrategy> strategies;
    private final List<FraudCheck> fraudChecks;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // Hardcoded network settlement account id for now based on seed data
    private static final String NETWORK_SETTLEMENT_ACCOUNT_ID = "acc-network-settle";
    // Assuming merchant 1 for demo purposes
    private static final String MERCHANT_ACCOUNT_ID = "acc-merchant-1";

    @Transactional
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        // Find card
        Card card = cardRepository.findById(request.getCardId())
                .orElseThrow(() -> new RuntimeException("Card not found"));

        if (!CardStatus.ACTIVE.name().equals(card.getStatus())) {
            return buildDeclineResponse(request, card, "CARD_NOT_ACTIVE");
        }

        // Run Fraud Checks (Chain of Responsibility)
        for (FraudCheck check : fraudChecks) {
            FraudResult result = check.check(request);
            if (!result.isPass()) {
                return buildDeclineResponse(request, card, result.getReason());
            }
        }

        // Lock the cardholder's account pessimistically
        Account account = accountRepository.findByIdForUpdate(card.getAccount().getId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Determine strategy
        // In a real system, we'd lookup merchant details to get country.
        // Assuming US merchant for domestic by default here for simplicity
        AuthorizationStrategy strategy = strategies.stream()
                .filter(s -> s.supports(request.getCurrency(), "US"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No suitable authorization strategy found"));

        BigDecimal fee = strategy.computeFee(request.getAmount());
        BigDecimal totalAmount = request.getAmount().add(fee);

        String ruleDecline = strategy.checkRules(totalAmount);
        if (ruleDecline != null) {
            return buildDeclineResponse(request, card, ruleDecline);
        }

        if (account.getAvailableBalance().compareTo(totalAmount) < 0) {
            return buildDeclineResponse(request, card, "INSUFFICIENT_FUNDS");
        }

        // Proceed to authorize
        account.setAvailableBalance(account.getAvailableBalance().subtract(totalAmount));
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setCard(card);
        transaction.setMerchantId(request.getMerchantId());
        transaction.setMcc(request.getMcc());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.AUTHORIZED.name());
        transaction = transactionRepository.save(transaction);

        // Double-entry ledger writes
        Account networkAccount = accountRepository.findByIdForUpdate(NETWORK_SETTLEMENT_ACCOUNT_ID)
                .orElseThrow(() -> new RuntimeException("Network settlement account missing"));
        networkAccount.setLedgerBalance(networkAccount.getLedgerBalance().add(totalAmount));
        accountRepository.save(networkAccount);

        LedgerEntry debit = new LedgerEntry();
        debit.setTransaction(transaction);
        debit.setAccount(account);
        debit.setDirection("D");
        debit.setAmount(totalAmount);
        ledgerEntryRepository.save(debit);

        LedgerEntry credit = new LedgerEntry();
        credit.setTransaction(transaction);
        credit.setAccount(networkAccount);
        credit.setDirection("C");
        credit.setAmount(totalAmount);
        ledgerEntryRepository.save(credit);

        return AuthorizeResponse.builder()
                .transactionId(transaction.getId())
                .status(TransactionStatus.AUTHORIZED.name())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .availableBalanceAfter(account.getAvailableBalance())
                .build();
    }

    private AuthorizeResponse buildDeclineResponse(AuthorizeRequest request, Card card, String reason) {
        Transaction transaction = new Transaction();
        transaction.setCard(card);
        transaction.setMerchantId(request.getMerchantId());
        transaction.setMcc(request.getMcc());
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setStatus(TransactionStatus.DECLINED.name());
        transaction.setDeclineReason(reason);
        transaction = transactionRepository.save(transaction);

        return AuthorizeResponse.builder()
                .transactionId(transaction.getId())
                .status(TransactionStatus.DECLINED.name())
                .declineReason(reason)
                .build();
    }

    @Transactional
    public void capture(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        TransactionStateMachine.validateTransition(
                TransactionStatus.valueOf(transaction.getStatus()),
                TransactionStatus.CAPTURED
        );

        Account networkAccount = accountRepository.findByIdForUpdate(NETWORK_SETTLEMENT_ACCOUNT_ID)
                .orElseThrow();
        Account merchantAccount = accountRepository.findByIdForUpdate(MERCHANT_ACCOUNT_ID)
                .orElseThrow();

        networkAccount.setLedgerBalance(networkAccount.getLedgerBalance().subtract(transaction.getAmount()));
        merchantAccount.setAvailableBalance(merchantAccount.getAvailableBalance().add(transaction.getAmount()));
        merchantAccount.setLedgerBalance(merchantAccount.getLedgerBalance().add(transaction.getAmount()));
        
        accountRepository.save(networkAccount);
        accountRepository.save(merchantAccount);

        transaction.setStatus(TransactionStatus.CAPTURED.name());
        transactionRepository.save(transaction);

        createLedgerPair(transaction, networkAccount, "D", merchantAccount, "C", transaction.getAmount());
    }

    @Transactional
    public void reverse(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        TransactionStateMachine.validateTransition(
                TransactionStatus.valueOf(transaction.getStatus()),
                TransactionStatus.REVERSED
        );

        Account networkAccount = accountRepository.findByIdForUpdate(NETWORK_SETTLEMENT_ACCOUNT_ID)
                .orElseThrow();
        Account cardholderAccount = accountRepository.findByIdForUpdate(transaction.getCard().getAccount().getId())
                .orElseThrow();

        networkAccount.setLedgerBalance(networkAccount.getLedgerBalance().subtract(transaction.getAmount()));
        cardholderAccount.setAvailableBalance(cardholderAccount.getAvailableBalance().add(transaction.getAmount()));
        
        accountRepository.save(networkAccount);
        accountRepository.save(cardholderAccount);

        transaction.setStatus(TransactionStatus.REVERSED.name());
        transactionRepository.save(transaction);

        createLedgerPair(transaction, networkAccount, "D", cardholderAccount, "C", transaction.getAmount());
    }

    @Transactional
    public void refund(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        TransactionStateMachine.validateTransition(
                TransactionStatus.valueOf(transaction.getStatus()),
                TransactionStatus.REFUNDED
        );

        Account merchantAccount = accountRepository.findByIdForUpdate(MERCHANT_ACCOUNT_ID)
                .orElseThrow();
        Account cardholderAccount = accountRepository.findByIdForUpdate(transaction.getCard().getAccount().getId())
                .orElseThrow();

        merchantAccount.setAvailableBalance(merchantAccount.getAvailableBalance().subtract(transaction.getAmount()));
        merchantAccount.setLedgerBalance(merchantAccount.getLedgerBalance().subtract(transaction.getAmount()));
        cardholderAccount.setAvailableBalance(cardholderAccount.getAvailableBalance().add(transaction.getAmount()));
        cardholderAccount.setLedgerBalance(cardholderAccount.getLedgerBalance().add(transaction.getAmount()));

        accountRepository.save(merchantAccount);
        accountRepository.save(cardholderAccount);

        transaction.setStatus(TransactionStatus.REFUNDED.name());
        transactionRepository.save(transaction);

        createLedgerPair(transaction, merchantAccount, "D", cardholderAccount, "C", transaction.getAmount());
    }

    private void createLedgerPair(Transaction tx, Account debitAcc, String debitDir, Account creditAcc, String creditDir, BigDecimal amount) {
        LedgerEntry debit = new LedgerEntry();
        debit.setTransaction(tx);
        debit.setAccount(debitAcc);
        debit.setDirection(debitDir);
        debit.setAmount(amount);
        ledgerEntryRepository.save(debit);

        LedgerEntry credit = new LedgerEntry();
        credit.setTransaction(tx);
        credit.setAccount(creditAcc);
        credit.setDirection(creditDir);
        credit.setAmount(amount);
        ledgerEntryRepository.save(credit);
        
        publishEvent(tx);
    }

    private void publishEvent(Transaction tx) {
        try {
            String payload = objectMapper.writeValueAsString(tx);
            eventPublisher.publishEvent(new TransactionEvent(this, tx.getId(), tx.getStatus(), payload));
        } catch (Exception e) {
            // Log error, but don't fail the transaction
            System.err.println("Failed to serialize transaction event: " + e.getMessage());
        }
    }
}
