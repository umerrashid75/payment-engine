package com.coreissuer.api.service;

import com.coreissuer.api.dto.AuthorizeRequest;
import com.coreissuer.api.dto.AuthorizeResponse;
import com.coreissuer.api.event.TransactionEvent;
import com.coreissuer.api.exception.AccountNotFoundException;
import com.coreissuer.api.exception.AuthorizationStrategyNotFoundException;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final List<AuthorizationStrategy> strategies;
    private final List<FraudCheck> fraudChecks;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private static final String NETWORK_SETTLEMENT_ACCOUNT_ID = "acc-network-settle";

    @Transactional
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        Card card = cardRepository.findById(request.getCardId())
                .orElseThrow(() -> new CardNotFoundException(request.getCardId()));

        if (!CardStatus.ACTIVE.name().equals(card.getStatus())) {
            return buildDeclineResponse(request, card, "CARD_NOT_ACTIVE");
        }

        for (FraudCheck check : fraudChecks) {
            FraudResult result = check.check(request);
            if (!result.isPass()) {
                return buildDeclineResponse(request, card, result.getReason());
            }
        }

        Account account = accountRepository.findByIdForUpdate(card.getAccount().getId())
                .orElseThrow(() -> new AccountNotFoundException(card.getAccount().getId()));

        AuthorizationStrategy strategy = strategies.stream()
                .filter(s -> s.supports(request.getCurrency(), "US"))
                .findFirst()
                .orElseThrow(() -> new AuthorizationStrategyNotFoundException(request.getCurrency(), "US"));

        BigDecimal fee = strategy.computeFee(request.getAmount());
        BigDecimal totalAmount = request.getAmount().add(fee);

        String ruleDecline = strategy.checkRules(totalAmount);
        if (ruleDecline != null) {
            return buildDeclineResponse(request, card, ruleDecline);
        }

        if (account.getAvailableBalance().compareTo(totalAmount) < 0) {
            return buildDeclineResponse(request, card, "INSUFFICIENT_FUNDS");
        }

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

        Account networkAccount = accountRepository.findByIdForUpdate(NETWORK_SETTLEMENT_ACCOUNT_ID)
                .orElseThrow(() -> new AccountNotFoundException(NETWORK_SETTLEMENT_ACCOUNT_ID));
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
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        TransactionStateMachine.validateTransition(
                TransactionStatus.valueOf(transaction.getStatus()),
                TransactionStatus.CAPTURED
        );

        Account networkAccount = accountRepository.findByIdForUpdate(NETWORK_SETTLEMENT_ACCOUNT_ID)
                .orElseThrow(() -> new AccountNotFoundException(NETWORK_SETTLEMENT_ACCOUNT_ID));
        String merchantAccountId = resolveMerchantAccountId(transaction.getMerchantId());
        Account merchantAccount = accountRepository.findByIdForUpdate(merchantAccountId)
                .orElseThrow(() -> new AccountNotFoundException(merchantAccountId));

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
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        TransactionStateMachine.validateTransition(
                TransactionStatus.valueOf(transaction.getStatus()),
                TransactionStatus.REVERSED
        );

        Account networkAccount = accountRepository.findByIdForUpdate(NETWORK_SETTLEMENT_ACCOUNT_ID)
                .orElseThrow(() -> new AccountNotFoundException(NETWORK_SETTLEMENT_ACCOUNT_ID));
        String cardholderAccountId = transaction.getCard().getAccount().getId();
        Account cardholderAccount = accountRepository.findByIdForUpdate(cardholderAccountId)
                .orElseThrow(() -> new AccountNotFoundException(cardholderAccountId));

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
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        TransactionStateMachine.validateTransition(
                TransactionStatus.valueOf(transaction.getStatus()),
                TransactionStatus.REFUNDED
        );

        String merchantAccountId = resolveMerchantAccountId(transaction.getMerchantId());
        Account merchantAccount = accountRepository.findByIdForUpdate(merchantAccountId)
                .orElseThrow(() -> new AccountNotFoundException(merchantAccountId));
        String cardholderAccountId = transaction.getCard().getAccount().getId();
        Account cardholderAccount = accountRepository.findByIdForUpdate(cardholderAccountId)
                .orElseThrow(() -> new AccountNotFoundException(cardholderAccountId));

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
            log.error("Failed to serialize transaction event txId={}", tx.getId(), e);
        }
    }

    // TODO: replace with a real merchant->account mapping table.
    private String resolveMerchantAccountId(String merchantId) {
        return "acc-merchant-" + merchantId;
    }
}
