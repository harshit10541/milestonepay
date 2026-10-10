package com.milestonepay.payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Low-level HTTP access to PayPal: OAuth token, JSON calls, error mapping.
 * <p>
 * Package-private on purpose: no module outside {@code payments} can call PayPal.
 * Everyone else goes through {@link PaymentService}.
 */
@Component
class PayPalClient {

    /** Refresh the token this long before PayPal says it expires. */
    private static final long TOKEN_SAFETY_SECONDS = 60;

    private final RestClient http;
    private final PayPalProperties props;
    private final ObjectMapper json;
    private final Clock clock;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    PayPalClient(RestClient.Builder builder, PayPalProperties props, ObjectMapper json, Clock clock) {
        this.http = builder.baseUrl(props.baseUrl()).build();
        this.props = props;
        this.json = json;
        this.clock = clock;
    }

    /**
     * POST a JSON body. {@code requestId} becomes the {@code PayPal-Request-Id} header,
     * so a retry with the same id returns the first result instead of acting twice.
     * Returns an empty object when PayPal answers 204 No Content.
     */
    JsonNode post(String path, Object body, String requestId) {
        return call(() -> {
            JsonNode response = http.post()
                    .uri(path)
                    .headers(h -> {
                        h.setBearerAuth(accessToken());
                        h.set("PayPal-Request-Id", requestId);
                        h.set("Prefer", "return=representation");
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return response == null ? json.createObjectNode() : response;
        });
    }

    JsonNode get(String path) {
        return call(() -> http.get()
                .uri(path)
                .headers(h -> h.setBearerAuth(accessToken()))
                .retrieve()
                .body(JsonNode.class));
    }

    /** Client-credentials token, cached until shortly before it expires. */
    synchronized String accessToken() {
        if (cachedToken != null && clock.instant().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        if (isBlank(props.clientId()) || isBlank(props.clientSecret())) {
            throw new IllegalStateException("PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET must be set");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        JsonNode token = call(() -> http.post()
                .uri("/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(props.clientId(), props.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
        cachedToken = token.path("access_token").asText(null);
        if (cachedToken == null) {
            throw new PayPalException(200, null, null, "token response had no access_token");
        }
        long expiresIn = token.path("expires_in").asLong(0);
        tokenExpiresAt = clock.instant().plusSeconds(Math.max(0, expiresIn - TOKEN_SAFETY_SECONDS));
        return cachedToken;
    }

    private JsonNode call(Supplier<JsonNode> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                clearToken(); // token revoked or expired early: next call fetches a fresh one
            }
            throw toPayPalException(e);
        } catch (ResourceAccessException e) {
            throw new PayPalException(0, null, null, "PayPal unreachable: " + e.getMessage());
        }
    }

    private synchronized void clearToken() {
        cachedToken = null;
        tokenExpiresAt = Instant.EPOCH;
    }

    private PayPalException toPayPalException(RestClientResponseException e) {
        String issue = null;
        String debugId = e.getResponseHeaders() == null ? null
                : e.getResponseHeaders().getFirst("PayPal-Debug-Id");
        String message = e.getStatusText();
        try {
            JsonNode body = json.readTree(e.getResponseBodyAsString());
            if (body != null) {
                issue = body.path("details").path(0).path("issue").asText(body.path("name").asText(null));
                debugId = body.path("debug_id").asText(debugId);
                message = body.path("message").asText(body.path("error_description").asText(message));
            }
        } catch (Exception ignored) {
            // body was not JSON; keep the HTTP status text
        }
        return new PayPalException(e.getStatusCode().value(), issue, debugId, message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
