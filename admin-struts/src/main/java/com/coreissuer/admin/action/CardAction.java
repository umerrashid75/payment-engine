package com.coreissuer.admin.action;

import com.coreissuer.common.domain.Card;
import com.coreissuer.common.domain.CardStatus;
import com.coreissuer.common.service.SharedCardService;
import com.opensymphony.xwork2.ActionSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CardAction extends ActionSupport {

    @Autowired
    private SharedCardService sharedCardService;

    private List<Card> cards;
    private String cardId;
    private String status;

    public String list() {
        cards = sharedCardService.findAll();
        return SUCCESS;
    }

    public String updateStatus() {
        if (cardId != null && status != null) {
            sharedCardService.updateStatus(cardId, CardStatus.valueOf(status));
        }
        return SUCCESS;
    }

    // Getters and Setters

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
