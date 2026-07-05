package com.coreissuer.common.factory;

import com.coreissuer.common.domain.Card;
import com.coreissuer.common.domain.CardStatus;
import com.coreissuer.common.domain.CardTier;
import com.coreissuer.common.util.CryptoUtils;
import com.coreissuer.common.util.LuhnUtils;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;

/**
 * Pattern: Factory
 * Creates configured Card entities based on the requested CardTier.
 */
@Component
public class CardFactory {

    private static final String BIN_STANDARD = "411111";
    private static final String BIN_PREMIUM = "422222";
    // CVVs are card credentials: they must not come from a predictable PRNG.
    private static final SecureRandom RANDOM = new SecureRandom();

    public Card create(CardTier tier, String pepper) {
        String bin = tier == CardTier.PREMIUM ? BIN_PREMIUM : BIN_STANDARD;
        String rawPan = LuhnUtils.generatePan(bin);
        String rawCvv = String.format("%03d", RANDOM.nextInt(1000));
        
        LocalDate expiry = LocalDate.now().plusYears(3);

        Card card = new Card();
        card.setPanLastFour(rawPan.substring(rawPan.length() - 4));
        card.setPanHash(CryptoUtils.hashWithPepper(rawPan, pepper));
        card.setCvvHash(CryptoUtils.hashWithPepper(rawCvv, pepper));
        card.setExpiryMonth(expiry.getMonthValue());
        card.setExpiryYear(expiry.getYear());
        card.setTier(tier.name());
        card.setStatus(CardStatus.ACTIVE.name());

        return card;
    }
}
