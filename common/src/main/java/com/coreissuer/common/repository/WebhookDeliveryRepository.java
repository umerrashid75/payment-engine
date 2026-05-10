package com.coreissuer.common.repository;

import com.coreissuer.common.domain.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {
    List<WebhookDelivery> findByStatus(String status);
}
