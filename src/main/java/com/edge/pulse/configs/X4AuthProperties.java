package com.edge.pulse.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * X4Auth (Tasdiq) OAuth 2.0 push-authentication configuration.
 *
 * <p>Mirrors the integration the x4mahara service uses against the same X4Auth
 * server: OAuth2 {@code x4auth_push} grant against {@code {baseUrl}/oauth/token},
 * with the resulting OIDC {@code id_token} verified via {@code {baseUrl}/.well-known/jwks.json}.
 *
 * <p>On the air-gapped k2 cluster, X4Auth is reached pod-to-pod via the in-cluster
 * service DNS, e.g. {@code http://x4auth-server.tasdiq-dev.svc.cluster.local:5000}.
 * All values are externalised; when {@code baseUrl}/{@code clientId} are blank the
 * service self-disables and {@code /api/auth/x4auth/config} reports {@code enabled:false}.
 */
@Configuration
@ConfigurationProperties(prefix = "x4auth")
@Getter
@Setter
public class X4AuthProperties {
    /** X4Auth API root (no trailing /v1). e.g. http://x4auth-server.tasdiq-dev.svc.cluster.local:5000 */
    private String baseUrl;
    /** OAuth client id registered for Pulse in the X4Auth admin portal. */
    private String clientId;
    /** OAuth client secret (k8s secret — never defaulted). */
    private String clientSecret;
    /** OIDC scopes requested on the push grant. */
    private String oauthScope = "openid profile email roles";
    /** HTTP timeout in milliseconds for calls to X4Auth. */
    private int timeout = 30000;
    /**
     * Login user-resolution strategy:
     *   EMAIL                   — legacy/transitional: match by email, auto-provision a bare EMPLOYEE if absent.
     *   EMPLOYEE_NUMBER_STRICT  — target: match by the x4auth:employeeId claim against a synced saf employee;
     *                             reject login if no match (no bare auto-provisioning).
     * Default EMAIL so Pulse can deploy before X4Auth emits the employeeId claim; k2 gitops flips to STRICT.
     */
    private String matchMode = "EMAIL";
}
