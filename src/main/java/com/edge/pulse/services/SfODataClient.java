package com.edge.pulse.services;

import com.edge.pulse.data.dto.SfUserRecord;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Thin OData v2 client for SAP SuccessFactors.
 *
 * <p>All requests are routed through a SOCKS5 jump server configured via
 * {@code pulse.sf.proxy.host} / {@code pulse.sf.proxy.port}.
 *
 * <p>Authentication uses HTTP Basic with the SF API service account.
 * The proxy is applied at the {@link RestClient} level via a custom
 * {@link org.springframework.http.client.SimpleClientHttpRequestFactory}.
 */
@Component
@Slf4j
public class SfODataClient {

    private final RestClient restClient;
    private final String authHeader;

    @Value("${pulse.sf.page-size:500}")
    private int pageSize;

    public SfODataClient(
            @Value("${pulse.sf.base-url}") String baseUrl,
            @Value("${pulse.sf.username}") String username,
            @Value("${pulse.sf.password}") String password,
            @Value("${pulse.sf.proxy.host:}") String proxyHost,
            @Value("${pulse.sf.proxy.port:1080}") int proxyPort
    ) {
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());

        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();

        if (proxyHost != null && !proxyHost.isBlank()) {
            factory.setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)));
            log.info("SfODataClient: using SOCKS5 proxy {}:{}", proxyHost, proxyPort);
        }

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, this.authHeader)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Fetches all active User records from SF using $skip-based pagination.
     * SF OData v2 does not return __next links; pagination is controlled via $skip.
     * Stops when a page returns fewer records than $top.
     */
    public List<SfUserRecord> fetchAllUsers() {
        String select = "userId,username,firstName,lastName,title,hireDate,isAlumni,status," +
                        "department,division,custom01,custom02,custom05,custom08,email,manager/userId";
        String baseUrl = "/User?$format=json&$top=" + pageSize +
                         "&$expand=manager&$select=" + select;
        return paginateWithSkip(baseUrl);
    }

    /**
     * Delta sync: fetches only users modified since the given timestamp.
     * Uses lastModifiedDateTime filter. Falls back to full if timestamp is null.
     */
    public List<SfUserRecord> fetchDeltaUsers(String sinceTimestamp) {
        if (sinceTimestamp == null || sinceTimestamp.isBlank()) {
            return fetchAllUsers();
        }
        String select = "userId,username,firstName,lastName,title,hireDate,isAlumni,status," +
                        "department,division,custom01,custom02,custom05,custom08,email,manager/userId";
        // SF OData filter for users modified since a datetime (ISO 8601 format)
        String baseUrl = "/User?$format=json&$top=" + pageSize +
                         "&$expand=manager&$select=" + select +
                         "&$filter=lastModifiedDateTime%20gt%20datetime'" + sinceTimestamp + "'";
        return paginateWithSkip(baseUrl);
    }

    // ── internals ──────────────────────────────────────────────────────────

    /**
     * Paginates using $skip offsets.
     * SF OData v2 does not return __next links for the User entity;
     * we increment $skip until the returned page is smaller than $top.
     */
    private List<SfUserRecord> paginateWithSkip(String baseUrl) {
        List<SfUserRecord> all = new ArrayList<>();
        int skip = 0;
        int page = 0;

        while (true) {
            String url = baseUrl + "&$skip=" + skip;
            try {
                ODataResponse resp = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(ODataResponse.class);

                if (resp == null || resp.d() == null) break;

                List<SfUserRecord> batch = resp.d().results();
                int batchSize = batch == null ? 0 : batch.size();
                if (batchSize > 0) all.addAll(batch);

                page++;
                log.debug("SfODataClient: page {} (skip={}) — fetched {} users (total: {})",
                        page, skip, batchSize, all.size());

                // Stop when the page is not full — no more data
                if (batchSize < pageSize) break;

                skip += pageSize;

            } catch (RestClientException e) {
                log.error("SfODataClient: HTTP error on page {} (skip={}) — {}", page, skip, e.getMessage());
                break;
            }
        }

        log.info("SfODataClient: fetched {} users in {} pages", all.size(), page);
        return all;
    }

    // ── OData v2 response envelope ─────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ODataResponse(@JsonProperty("d") ODataResults d) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ODataResults(
            @JsonProperty("results") List<SfUserRecord> results
    ) {}
}
