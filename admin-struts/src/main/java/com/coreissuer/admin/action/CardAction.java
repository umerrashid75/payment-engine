package com.coreissuer.admin.action;

import com.coreissuer.common.domain.Card;
import com.coreissuer.common.domain.CardStatus;
import com.coreissuer.common.service.SharedCardService;
import com.opensymphony.xwork2.ActionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Struts actions hold per-request state (form fields), so they must be
 * prototype-scoped — a singleton would share request data across threads.
 */
@Component
@Scope("prototype")
public class CardAction extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(CardAction.class);

    @Autowired
    private transient SharedCardService sharedCardService;

    private List<Card> cards;
    private String cardId;
    private String status;

    public String list() {
        cards = sharedCardService.findAll();
        return SUCCESS;
    }

    public String updateStatus() {
        if (cardId != null && status != null) {
            try {
                sharedCardService.updateStatus(cardId, CardStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                log.warn("Rejected card status update: cardId={} status={} reason={}", cardId, status, e.getMessage());
                addActionError("Could not update card status.");
            }
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
