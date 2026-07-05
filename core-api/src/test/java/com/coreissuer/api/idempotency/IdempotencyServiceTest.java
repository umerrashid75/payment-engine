package com.coreissuer.api.idempotency;

import com.coreissuer.common.domain.IdempotencyRecord;
import com.coreissuer.common.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String KEY = "key-1";
    private static final String HASH = "hash-a";
    private static final String OTHER_HASH = "hash-b";
    private static final String BODY = "{\"status\":\"AUTHORIZED\"}";

    @Mock
    private IdempotencyRecordRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, new IdempotencyCache());
    }

    @Test
    @DisplayName("first call returns empty and takes the in-flight marker")
    void firstCall_returnsEmpty() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());

        assertThat(service.getCachedResponse(KEY, HASH)).isEmpty();
    }

    @Test
    @DisplayName("concurrent call with the same key is rejected while processing")
    void concurrentCall_sameKey_conflicts() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        service.getCachedResponse(KEY, HASH);

        assertThatThrownBy(() -> service.getCachedResponse(KEY, HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already being processed");
    }

    @Test
    @DisplayName("replay with the same payload returns the cached response")
    void replay_sameHash_returnsCachedBody() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        service.getCachedResponse(KEY, HASH);
        service.saveResponse(KEY, "/endpoint", HASH, 200, BODY);

        assertThat(service.getCachedResponse(KEY, HASH)).contains(BODY);
    }

    @Test
    @DisplayName("memory-cache hit with a different payload hash is rejected, not replayed")
    void replay_differentHash_conflictsOnMemoryHit() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        service.getCachedResponse(KEY, HASH);
        service.saveResponse(KEY, "/endpoint", HASH, 200, BODY);

        assertThatThrownBy(() -> service.getCachedResponse(KEY, OTHER_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName("DB hit with a different payload hash is rejected")
    void replay_differentHash_conflictsOnDbHit() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(KEY);
        record.setRequestHash(HASH);
        record.setResponseBody(BODY);
        when(repository.findById(KEY)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.getCachedResponse(KEY, OTHER_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName("DB hit with the same payload hash returns the stored response")
    void replay_sameHash_returnsDbBody() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(KEY);
        record.setRequestHash(HASH);
        record.setResponseBody(BODY);
        when(repository.findById(KEY)).thenReturn(Optional.of(record));

        assertThat(service.getCachedResponse(KEY, HASH)).contains(BODY);
    }

    @Test
    @DisplayName("clearProcessing releases the marker so a retry can proceed")
    void clearProcessing_allowsRetry() {
        when(repository.findById(KEY)).thenReturn(Optional.empty());
        service.getCachedResponse(KEY, HASH);

        service.clearProcessing(KEY);

        assertThat(service.getCachedResponse(KEY, HASH)).isEmpty();
    }

    @Test
    @DisplayName("clearProcessing never evicts a completed response")
    void clearProcessing_keepsCompletedEntry() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        service.getCachedResponse(KEY, HASH);
        service.saveResponse(KEY, "/endpoint", HASH, 200, BODY);

        service.clearProcessing(KEY);

        assertThat(service.getCachedResponse(KEY, HASH)).contains(BODY);
    }
}
