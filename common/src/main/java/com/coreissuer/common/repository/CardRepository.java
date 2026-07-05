package com.coreissuer.common.repository;

import com.coreissuer.common.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardRepository extends JpaRepository<Card, String> {
    List<Card> findByAccountId(String accountId);

    /** Fetch-join so detached entities are safe to render (open-in-view is off). */
    @Query("SELECT c FROM Card c JOIN FETCH c.account")
    List<Card> findAllWithAccounts();
}
