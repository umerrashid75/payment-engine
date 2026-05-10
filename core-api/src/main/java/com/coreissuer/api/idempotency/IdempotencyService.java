package com.coreissuer.api.idempotency;

import com.coreissuer.common.domain.IdempotencyRecord;
import com.coreissuer.common.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final IdempotencyCache cache;

    public Optional<String> getCachedResponse(String idempotencyKey, String requestHash) {
        // 1. Check in-memory cache first
        String cachedResponse = cache.get(idempotencyKey);
        if (cachedResponse != null && !cachedResponse.equals("PROCESSING")) {
            return Optional.of(cachedResponse);
        }

        // 2. Check DB
        Optional<IdempotencyRecord> recordOpt = repository.findById(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("Idempotency key collision with different request payload (409 Conflict)");
            }
            cache.put(idempotencyKey, record.getResponseBody());
            return Optional.of(record.getResponseBody());
        }

        // 3. Mark as processing in cache
        if (!cache.putIfAbsent(idempotencyKey, "PROCESSING")) {
             throw new IllegalStateException("Request is already processing (429 Too Many Requests or 409 Conflict)");
        }
        
        return Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveResponse(String idempotencyKey, String endpoint, String requestHash, int status, String responseBody) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setEndpoint(endpoint);
        record.setRequestHash(requestHash);
        record.setResponseStatus(status);
        record.setResponseBody(responseBody);
        repository.save(record);

        cache.put(idempotencyKey, responseBody);
    }
    
    public void clearProcessing(String idempotencyKey) {
        if ("PROCESSING".equals(cache.get(idempotencyKey))) {
            cache.remove(idempotencyKey);
        }
    }
}
