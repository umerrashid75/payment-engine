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

    /**
     * Returns the stored response for a replayed request, empty if the caller
     * should process the request, or throws IllegalStateException (mapped to
     * 409) when the key is reused with a different payload or still in flight.
     */
    public Optional<String> getCachedResponse(String idempotencyKey, String requestHash) {
        CachedIdempotentResponse cached = cache.get(idempotencyKey);
        if (cached != null) {
            if (!cached.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("Idempotency key reused with a different request payload");
            }
            if (cached.isProcessing()) {
                throw new IllegalStateException("A request with this idempotency key is already being processed");
            }
            return Optional.of(cached.getResponseBody());
        }

        Optional<IdempotencyRecord> recordOpt = repository.findById(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecord record = recordOpt.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IllegalStateException("Idempotency key reused with a different request payload");
            }
            cache.putResponse(idempotencyKey, record.getRequestHash(), record.getResponseBody());
            return Optional.of(record.getResponseBody());
        }

        if (!cache.markProcessing(idempotencyKey, requestHash)) {
            throw new IllegalStateException("A request with this idempotency key is already being processed");
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

        cache.putResponse(idempotencyKey, requestHash, responseBody);
    }

    public void clearProcessing(String idempotencyKey) {
        cache.removeIfProcessing(idempotencyKey);
    }
}
