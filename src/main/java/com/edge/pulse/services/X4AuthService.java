package com.edge.pulse.services;

import com.edge.pulse.configs.X4AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.security.Key;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Brokers X4Auth (Tasdiq) OAuth 2.0 {@code x4auth_push} authentication for Pulse.
 *
 * <p>Replicates the proven x4mahara flow (see {@code XMaharaServer/src/services/x4auth.service.ts}):
 * <ol>
 *   <li>{@link #initiate(String)} — POST {@code /oauth/token} (grant {@code x4auth_push}) → 202 + transaction_id,
 *       triggers a push to the user's Tasdiq app. The {@code poll_nonce} is stashed in Redis so any pod can poll
 *       (x4mahara kept it in-memory and is therefore single-replica only — Pulse uses Redis).</li>
 *   <li>{@link #poll(String)} — POST {@code /oauth/token} echoing the nonce → 202 pending / 200 + id_token / denied.</li>
 *   <li>id_token (OIDC RS256) verified via JWKS with issuer + audience enforced.</li>
 * </ol>
 * This service only authenticates; the controller maps the verified identity to a Pulse user and mints Pulse JWTs.
 */
@Service
@Slf4j
public class X4AuthService {

    /** Outcome of an initiate call. */
    public record InitiateResult(boolean success, String transactionId, Integer pollIntervalMs,
                                 String verificationCode, String errorCode, String errorMessage) {
        static InitiateResult ok(String txn, Integer pollMs, String code) {
            return new InitiateResult(true, txn, pollMs, code, null, null);
        }
        static InitiateResult failed(String code, String msg) {
            return new InitiateResult(false, null, null, null, code, msg);
        }
    }

    /** Outcome of a poll. status ∈ {pending, approved, denied, expired}. */
    public record PollResult(String status, String email, String displayName, String department) {
        public boolean approved() { return "approved".equals(status); }
    }

    private static final String NONCE_PREFIX = "x4auth:nonce:";
    private static final String APPROVED_PREFIX = "x4auth:approved:";

    private final X4AuthProperties props;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient client;
    private final RestClient jwksClient;
    private final AtomicReference<JwkSet> jwksCache = new AtomicReference<>();

    public X4AuthService(X4AuthProperties props, StringRedisTemplate redis,
                         ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.redis = redis;
        this.objectMapper = objectMapper;
        if (props.getBaseUrl() != null && !props.getBaseUrl().isBlank()) {
            // Both clients are built from the (per-injection) builder so they share one
            // request factory — which lets MockRestServiceServer intercept both in tests.
            // Mirrors MicrosoftGraphService's use of the injected RestClient.Builder.
            this.client = restClientBuilder.baseUrl(props.getBaseUrl()).build();
            this.jwksClient = restClientBuilder.build();
        } else {
            this.client = null;
            this.jwksClient = null;
        }
    }

    public boolean isConfigured() {
        return client != null
                && props.getClientId() != null && !props.getClientId().isBlank()
                && props.getClientSecret() != null && !props.getClientSecret().isBlank();
    }

    /** Triggers a push challenge to the user's Tasdiq app. */
    public InitiateResult initiate(String email) {
        if (!isConfigured()) {
            return InitiateResult.failed("X4AUTH_NOT_CONFIGURED", "Enterprise authentication is not configured");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "x4auth_push");
        body.put("client_id", props.getClientId());
        body.put("client_secret", props.getClientSecret());
        body.put("email", email);
        body.put("scope", props.getOauthScope());

        final InitiateResult[] out = {InitiateResult.failed("AUTH_ERROR", "Failed to initiate authentication")};
        try {
            client.post().uri("/oauth/token").body(body).exchange((req, res) -> {
                HttpStatusCode sc = res.getStatusCode();
                String raw = res.bodyTo(String.class);
                JsonNode json = (raw == null || raw.isBlank()) ? null : objectMapper.readTree(raw);
                if (sc.is2xxSuccessful() && json != null && json.hasNonNull("transaction_id")) {
                    String txn = json.get("transaction_id").asText();
                    String nonce = json.path("poll_nonce").asText(null);
                    if (nonce != null && !nonce.isBlank()) {
                        long ttl = json.path("expires_in").asLong(120) + 30;
                        redis.opsForValue().set(NONCE_PREFIX + txn, nonce, ttl, TimeUnit.SECONDS);
                    }
                    int pollMs = json.path("poll_interval").asInt(2) * 1000;
                    String code = json.path("verification_code").asText(null);
                    out[0] = InitiateResult.ok(txn, pollMs,
                            (code != null && code.matches("[0-9]{4,8}")) ? code : null);
                } else {
                    out[0] = mapInitiateError(json);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("X4Auth initiate failed for {}: {}", email, e.getMessage());
        }
        return out[0];
    }

    /** Polls the transaction; verifies the id_token on approval. */
    public PollResult poll(String transactionId) {
        if (!isConfigured()) {
            return new PollResult("expired", null, null, null);
        }
        String nonce = redis.opsForValue().get(NONCE_PREFIX + transactionId);
        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "x4auth_push");
        body.put("client_id", props.getClientId());
        body.put("client_secret", props.getClientSecret());
        body.put("transaction_id", transactionId);
        if (nonce != null) {
            body.put("poll_nonce", nonce);
        }

        final PollResult[] out = {new PollResult("pending", null, null, null)};
        try {
            client.post().uri("/oauth/token").body(body).exchange((req, res) -> {
                HttpStatusCode sc = res.getStatusCode();
                String raw = res.bodyTo(String.class);
                JsonNode json = (raw == null || raw.isBlank()) ? null : objectMapper.readTree(raw);
                if (sc.value() == 202) {
                    out[0] = new PollResult("pending", null, null, null);
                } else if (sc.is2xxSuccessful() && json != null && json.hasNonNull("id_token")) {
                    Claims c = verifyIdToken(json.get("id_token").asText());
                    PollResult approved = new PollResult("approved",
                            c.get("email", String.class),
                            c.get("name", String.class),
                            c.get("x4auth:department", String.class));
                    out[0] = approved;
                    // X4Auth returns the id_token ONCE — cache the verified identity so a
                    // subsequent /complete (different request, possibly different pod) can
                    // consume it without re-polling the now-spent transaction.
                    cacheApproved(transactionId, approved);
                    redis.delete(NONCE_PREFIX + transactionId);
                } else if (json != null && json.hasNonNull("error")) {
                    String err = json.get("error").asText();
                    if ("access_denied".equals(err)) {
                        out[0] = new PollResult("denied", null, null, null);
                    } else if ("expired_token".equals(err)) {
                        out[0] = new PollResult("expired", null, null, null);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("X4Auth poll failed for txn {}: {}", transactionId, e.getMessage());
        }
        return out[0];
    }

    private void cacheApproved(String transactionId, PollResult approved) {
        try {
            redis.opsForValue().set(APPROVED_PREFIX + transactionId,
                    objectMapper.writeValueAsString(approved), 120, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache approved X4Auth identity for txn {}: {}", transactionId, e.getMessage());
        }
    }

    /**
     * Atomically retrieves and removes the cached approved identity for a transaction.
     * Falls back to a fresh {@link #poll(String)} if no cache exists (e.g. the client called
     * /complete without a prior /status poll). Returns {@code null} if not approved.
     */
    public PollResult consumeApproved(String transactionId) {
        String json = redis.opsForValue().getAndDelete(APPROVED_PREFIX + transactionId);
        if (json != null) {
            try {
                return objectMapper.readValue(json, PollResult.class);
            } catch (Exception e) {
                log.warn("Failed to read cached X4Auth identity for txn {}: {}", transactionId, e.getMessage());
            }
        }
        PollResult fresh = poll(transactionId);
        if (fresh.approved()) {
            // poll() just cached it; remove so it cannot be replayed.
            redis.delete(APPROVED_PREFIX + transactionId);
            return fresh;
        }
        return null;
    }

    /**
     * Verifies an X4Auth id_token (RS256) against the IdP's JWKS, enforcing issuer and audience.
     * Issuer + audience MUST be enforced — without them any key in the JWKS set could forge a token
     * (flagged HIGH in the x4mahara reference).
     */
    private Claims verifyIdToken(String idToken) {
        Locator<Key> keyLocator = header -> resolveSigningKey((String) header.get("kid"));
        return Jwts.parser()
                .keyLocator(keyLocator)
                .requireIssuer(props.getBaseUrl())
                .requireAudience(props.getClientId())
                .build()
                .parseSignedClaims(idToken)
                .getPayload();
    }

    private Key resolveSigningKey(String kid) {
        JwkSet set = jwksCache.get();
        if (set == null || findByKid(set, kid) == null) {
            String json = jwksClient.get().uri("/.well-known/jwks.json").retrieve().body(String.class);
            set = Jwks.setParser().build().parse(json);
            jwksCache.set(set);
        }
        Jwk<?> jwk = findByKid(set, kid);
        if (jwk == null) {
            throw new JwtException("No JWKS key found for kid=" + kid);
        }
        return jwk.toKey();
    }

    private Jwk<?> findByKid(JwkSet set, String kid) {
        if (kid == null) {
            return null;
        }
        for (Jwk<?> jwk : set.getKeys()) {
            if (kid.equals(jwk.getId())) {
                return jwk;
            }
        }
        return null;
    }

    private InitiateResult mapInitiateError(JsonNode json) {
        if (json != null && json.hasNonNull("error")) {
            String desc = json.path("error_description").asText("");
            if (desc.contains("not found")) {
                return InitiateResult.failed("USER_NOT_FOUND",
                        "No enterprise account found for this email. Please contact your IT administrator.");
            }
            if (desc.contains("no device")) {
                return InitiateResult.failed("NO_DEVICES",
                        "No Tasdiq app registered. Please set up Tasdiq on your mobile device first.");
            }
        }
        return InitiateResult.failed("AUTH_ERROR", "Failed to initiate authentication. Please try again.");
    }
}
