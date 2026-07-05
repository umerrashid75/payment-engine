package com.coreissuer.api.webhook;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Pattern: Adapter
 */
@Component
public class HttpWebhookAdapter {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestTemplate restTemplate;

    public HttpWebhookAdapter(RestTemplateBuilder restTemplateBuilder) {
        // Timeouts are mandatory: a hung merchant endpoint must not wedge the
        // scheduler thread that delivers every other webhook.
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setReadTimeout(READ_TIMEOUT)
                .build();
    }

    public void deliver(String url, String payload) {
        restTemplate.postForObject(url, payload, String.class);
    }
}
