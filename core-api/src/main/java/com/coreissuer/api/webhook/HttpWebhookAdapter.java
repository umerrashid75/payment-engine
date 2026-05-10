package com.coreissuer.api.webhook;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Pattern: Adapter
 */
@Component
public class HttpWebhookAdapter {

    private final RestTemplate restTemplate;

    public HttpWebhookAdapter() {
        this.restTemplate = new RestTemplate();
    }

    public void deliver(String url, String payload) {
        // Simple POST
        restTemplate.postForObject(url, payload, String.class);
    }
}
