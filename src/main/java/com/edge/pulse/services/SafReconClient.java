package com.edge.pulse.services;

import com.edge.pulse.configs.SafReconProperties;
import com.edge.pulse.data.dto.safrecon.SafReconEmployeePage;
import com.edge.pulse.data.dto.safrecon.SafReconOrgUnit;
import com.edge.pulse.data.dto.safrecon.SafReconTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin REST client for saf-recon-server. Holds a cached bearer token (saf-recon tokens are
 * short-lived, ~15 min); transparently re-authenticates once on a 401 and retries.
 * Self-disables when not configured (blank base-url/credentials) so non-k2 contexts are unaffected.
 */
@Service
@Slf4j
public class SafReconClient {

    private final SafReconProperties props;
    private final RestClient client;
    private volatile String token;

    public SafReconClient(SafReconProperties props, RestClient.Builder builder) {
        this.props = props;
        this.client = (props.getBaseUrl() != null && !props.getBaseUrl().isBlank())
                ? builder.baseUrl(props.getBaseUrl()).build() : null;
    }

    public boolean isConfigured() {
        return client != null
                && props.getUsername() != null && !props.getUsername().isBlank()
                && props.getPassword() != null && !props.getPassword().isBlank();
    }

    private synchronized String authenticate() {
        SafReconTokenResponse resp = client.post().uri("/api/v1/auth/token")
                .body(Map.of("username", props.getUsername(), "password", props.getPassword()))
                .retrieve().body(SafReconTokenResponse.class);
        if (resp == null || resp.token() == null) {
            throw new IllegalStateException("saf-recon auth returned no token");
        }
        this.token = resp.token();
        return this.token;
    }

    /** GET with bearer auth; on 401 re-auth once and retry. */
    private <T> T get(String uri, ParameterizedTypeReference<T> type) {
        // Double-checked lock: a concurrent manual sync (B6) + scheduled sync must not both
        // race a null token into two separate auth calls.
        if (token == null) {
            synchronized (this) {
                if (token == null) authenticate();
            }
        }
        try {
            return doGet(uri, type);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.info("saf-recon 401 — re-authenticating");
                authenticate();
                return doGet(uri, type);
            }
            throw e;
        }
    }

    private <T> T doGet(String uri, ParameterizedTypeReference<T> type) {
        return client.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve().body(type);
    }

    public List<SafReconOrgUnit> fetchOrgUnits() {
        List<SafReconOrgUnit> units = get("/api/v1/org-units", new ParameterizedTypeReference<>() {});
        return units != null ? units : List.of();
    }

    public SafReconEmployeePage fetchEmployeesPage(int page, int size) {
        SafReconEmployeePage p = get("/api/v1/employees?page=" + page + "&size=" + size,
                new ParameterizedTypeReference<>() {});
        return p != null ? p : new SafReconEmployeePage(List.of(), 0);
    }
}
