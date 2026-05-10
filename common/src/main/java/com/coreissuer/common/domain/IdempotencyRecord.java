package com.coreissuer.common.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_record")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", length = 80, nullable = false)
    private String idempotencyKey;

    @Column(length = 80, nullable = false)
    private String endpoint;

    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "JSON", nullable = false)
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
