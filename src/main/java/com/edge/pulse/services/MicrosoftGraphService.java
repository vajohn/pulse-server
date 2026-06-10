package com.edge.pulse.services;

import com.edge.pulse.data.dto.GraphGroup;
import com.edge.pulse.data.dto.GraphUserProfile;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class MicrosoftGraphService {

    private static final String GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0";
    private static final String LOGIN_BASE_URL  = "https://login.microsoftonline.com";

    private final RestClient restClient;
    private final RestClient loginClient;

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;

    public MicrosoftGraphService(
            RestClient.Builder restClientBuilder,
            @Value("${spring.cloud.azure.active-directory.profile.tenant-id:}") String tenantId,
            @Value("${spring.cloud.azure.active-directory.credential.client-id:}") String clientId,
            @Value("${spring.cloud.azure.active-directory.credential.client-secret:}") String clientSecret) {
        this.restClient    = restClientBuilder.baseUrl(GRAPH_BASE_URL).build();
        this.loginClient   = RestClient.builder().baseUrl(LOGIN_BASE_URL).build();
        this.tenantId      = tenantId;
        this.clientId      = clientId;
        this.clientSecret  = clientSecret;
    }

    // -------------------------------------------------------------------------
    // Delegated-access (user token) helpers — used at login time
    // -------------------------------------------------------------------------

    public Optional<GraphUserProfile> fetchMyProfile(String accessToken) {
        try {
            GraphUserProfile profile = restClient.get()
                    .uri("/me?$select=id,displayName,mail,jobTitle,department,employeeId,officeLocation,companyName")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GraphUserProfile.class);
            return Optional.ofNullable(profile);
        } catch (RestClientException e) {
            log.warn("Failed to fetch Graph /me profile: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<GraphUserProfile> fetchMyManager(String accessToken) {
        try {
            GraphUserProfile manager = restClient.get()
                    .uri("/me/manager?$select=id,displayName,mail")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GraphUserProfile.class);
            return Optional.ofNullable(manager);
        } catch (HttpClientErrorException.Forbidden e) {
            log.info("Graph /me/manager returned 403 — admin consent for User.Read.All likely not granted");
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Graph /me/manager returned 404 — no manager set in Azure AD");
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Failed to fetch Graph /me/manager: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // App-only (client credentials) helpers — used by directory sync
    // -------------------------------------------------------------------------

    /**
     * Acquires an app-only access token via client credentials flow.
     * scope = https://graph.microsoft.com/.default (requires admin-consented app permissions)
     */
    public String acquireAppOnlyToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type",    "client_credentials");
        form.add("client_id",     clientId);
        form.add("client_secret", clientSecret);
        form.add("scope",         "https://graph.microsoft.com/.default");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = loginClient.post()
                .uri("/" + tenantId + "/oauth2/v2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("acquireAppOnlyToken: no access_token in response");
        }
        return (String) response.get("access_token");
    }

    /**
     * Fetches all users from Azure AD (paginated).
     * Requires User.Read.All app permission.
     */
    public List<GraphUserProfile> fetchAllUsers(String appToken) {
        String url = GRAPH_BASE_URL +
                "/users?$select=id,displayName,mail,userPrincipalName,jobTitle,department,employeeId," +
                "officeLocation,companyName,accountEnabled&$top=999";
        return fetchAllPages(appToken, url, GraphUserProfile.class);
    }

    /**
     * Fetches all groups from Azure AD (paginated).
     * Requires Directory.Read.All app permission.
     */
    public List<GraphGroup> fetchAllGroups(String appToken) {
        String url = GRAPH_BASE_URL + "/groups?$select=id,displayName,description&$top=999";
        return fetchAllPages(appToken, url, GraphGroup.class);
    }

    /**
     * Fetches the manager of a specific user by their Graph user ID.
     * Returns empty if the user has no manager or the call is not consented.
     */
    public Optional<GraphUserProfile> fetchUserManagerById(String appToken, String userId) {
        try {
            GraphUserProfile manager = restClient.get()
                    .uri("/users/{id}/manager?$select=id,displayName,mail", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken)
                    .retrieve()
                    .body(GraphUserProfile.class);
            return Optional.ofNullable(manager);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden e) {
            log.debug("fetchUserManagerById: 403 for userId={}", userId);
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("fetchUserManagerById failed for userId={}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Pagination helper
    // -------------------------------------------------------------------------

    /** Follows @odata.nextLink pages and accumulates all items into a flat list. */
    private <T> List<T> fetchAllPages(String appToken, String firstUrl, Class<T> elementType) {
        List<T> all = new ArrayList<>();
        String url = firstUrl;
        while (url != null) {
            GraphPage<T> page = fetchPage(appToken, url, elementType);
            if (page.value() != null) {
                all.addAll(page.value());
            }
            url = page.nextLink();
        }
        return all;
    }

    @SuppressWarnings("unchecked")
    private <T> GraphPage<T> fetchPage(String appToken, String url, Class<T> elementType) {
        // Deserialize to a raw map first so we can handle both first-page (relative base) and
        // nextLink (absolute URL) transparently.
        Map<String, Object> raw = restClient.get()
                .uri(url.startsWith("http") ? url.replace(GRAPH_BASE_URL, "") : url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appToken)
                .retrieve()
                .body(Map.class);

        List<T> items = new ArrayList<>();
        if (raw != null && raw.get("value") instanceof List<?> valueList) {
            for (Object item : valueList) {
                if (item instanceof Map<?, ?> itemMap) {
                    // Re-serialize and deserialize via Jackson ObjectMapper would be cleanest,
                    // but RestClient body() already deserialized — use a typed re-fetch approach.
                    // Instead we rely on Jackson's @JsonIgnoreProperties and cast via a typed wrapper.
                    items.add(elementType.cast(deserializeItem(itemMap, elementType)));
                }
            }
        }
        String nextLink = raw != null ? (String) raw.get("@odata.nextLink") : null;
        return new GraphPage<>(items, nextLink);
    }

    /** Converts a raw map item to the target DTO type using field name mapping. */
    @SuppressWarnings("unchecked")
    private <T> T deserializeItem(Map<?, ?> map, Class<T> type) {
        if (type == GraphUserProfile.class) {
            return type.cast(new GraphUserProfile(
                    (String) map.get("id"),
                    (String) map.get("displayName"),
                    (String) map.get("mail"),
                    (String) map.get("userPrincipalName"),
                    (String) map.get("jobTitle"),
                    (String) map.get("department"),
                    (String) map.get("employeeId"),
                    (String) map.get("officeLocation"),
                    (String) map.get("companyName"),
                    map.get("accountEnabled") instanceof Boolean b ? b : null
            ));
        }
        if (type == GraphGroup.class) {
            return type.cast(new GraphGroup(
                    (String) map.get("id"),
                    (String) map.get("displayName"),
                    (String) map.get("description")
            ));
        }
        throw new IllegalArgumentException("Unsupported Graph DTO type: " + type);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GraphPage<T>(
            List<T> value,
            @JsonProperty("@odata.nextLink") String nextLink
    ) {}
}
