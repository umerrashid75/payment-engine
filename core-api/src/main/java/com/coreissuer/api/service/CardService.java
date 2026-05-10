package com.coreissuer.api.service;

import com.coreissuer.api.dto.CardResponse;
import com.coreissuer.api.dto.ProvisionCardRequest;
import com.coreissuer.common.domain.Account;
import com.coreissuer.common.domain.AccountType;
import com.coreissuer.common.domain.Card;
import com.coreissuer.common.factory.CardFactory;
import com.coreissuer.common.repository.AccountRepository;
import com.coreissuer.common.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final CardFactory cardFactory;

    @Value("${coreissuer.security.pepper:default-secret-pepper}")
    private String pepper;

    @Transactional
    public CardResponse provisionCard(ProvisionCardRequest request) {
        // 1. Create a new Account for the cardholder
        Account account = new Account();
        account.setType(AccountType.CARDHOLDER);
        account.setCurrency(request.getCurrency());
        account.setAvailableBalance(request.getInitialBalance());
        account.setLedgerBalance(request.getInitialBalance());
        account = accountRepository.save(account);

        // 2. Create the Card using the Factory
        Card card = cardFactory.create(request.getTier(), pepper);
        card.setAccount(account);
        card = cardRepository.save(card);

        return mapToResponse(card, account);
    }

    @Transactional(readOnly = true)
    public CardResponse getCard(String id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Card not found"));
        return mapToResponse(card, card.getAccount());
    }

    private CardResponse mapToResponse(Card card, Account account) {
        return CardResponse.builder()
                .id(card.getId())
                .accountId(account.getId())
                .panLastFour(card.getPanLastFour())
                .expiryMonth(card.getExpiryMonth())
                .expiryYear(card.getExpiryYear())
                .tier(card.getTier())
                .status(card.getStatus())
                .availableBalance(account.getAvailableBalance())
                .currency(account.getCurrency())
                .createdAt(card.getCreatedAt())
                .build();
    }
}
