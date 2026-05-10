package com.coreissuer.common.service;

import com.coreissuer.common.domain.Card;
import com.coreissuer.common.domain.CardStatus;
import com.coreissuer.common.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SharedCardService {

    private final CardRepository cardRepository;

    public List<Card> findAll() {
        return cardRepository.findAll();
    }

    @Transactional
    public void updateStatus(String cardId, CardStatus newStatus) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found"));
        card.setStatus(newStatus.name());
        cardRepository.save(card);
    }
}
