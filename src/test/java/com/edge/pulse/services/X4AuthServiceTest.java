package com.edge.pulse.services;

import com.edge.pulse.configs.X4AuthProperties;
import com.edge.pulse.services.X4AuthService.InitiateResult;
import com.edge.pulse.services.X4AuthService.PollResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * L1 verification (no live X4Auth) of the OAuth2 {@code x4auth_push} protocol handling:
 * the {@code /oauth/token} 202→200 push flow, error mapping, Redis nonce persistence, and
 * end-to-end JWKS RS256 verification of the {@code id_token} using a locally generated key.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class X4AuthServiceTest {

    private static final String BASE = "http://x4auth.test:5000";
    private static final String CLIENT_ID = "pulse";

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private X4AuthService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);

        X4AuthProperties props = new X4AuthProperties();
        props.setBaseUrl(BASE);
        props.setClientId(CLIENT_ID);
        props.setClientSecret("secret");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new X4AuthService(props, redis, new ObjectMapper(), builder);
    }

    @Test
    void isConfigured_trueWhenBaseUrlAndClientPresent() {
        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    void initiate_success_parsesTxnAndStoresNonce() {
        server.expect(requestTo(BASE + "/oauth/token")).andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"transaction_id\":\"PI-001\",\"poll_nonce\":\"nonce-xyz\",\"verification_code\":\"4821\","
                                + "\"poll_interval\":2,\"expires_in\":120}", MediaType.APPLICATION_JSON));

        InitiateResult r = service.initiate("jane@edge.ae");

        assertThat(r.success()).isTrue();
        assertThat(r.transactionId()).isEqualTo("PI-001");
        assertThat(r.verificationCode()).isEqualTo("4821");
        // nonce persisted to Redis so any pod can poll
        org.mockito.Mockito.verify(valueOps)
                .set(org.mockito.ArgumentMatchers.eq("x4auth:nonce:PI-001"),
                     org.mockito.ArgumentMatchers.eq("nonce-xyz"),
                     org.mockito.ArgumentMatchers.anyLong(),
                     org.mockito.ArgumentMatchers.any());
        server.verify();
    }

    @Test
    void initiate_userNotFound_mapsErrorCode() {
        server.expect(requestTo(BASE + "/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid_request\",\"error_description\":\"user not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        InitiateResult r = service.initiate("ghost@edge.ae");

        assertThat(r.success()).isFalse();
        assertThat(r.errorCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void poll_pending_returnsPending() {
        lenient().when(valueOps.get("x4auth:nonce:PI-001")).thenReturn("nonce-xyz");
        server.expect(requestTo(BASE + "/oauth/token"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).body("{}").contentType(MediaType.APPLICATION_JSON));

        PollResult p = service.poll("PI-001");

        assertThat(p.status()).isEqualTo("pending");
        assertThat(p.approved()).isFalse();
    }

    @Test
    void poll_accessDenied_returnsDenied() {
        server.expect(requestTo(BASE + "/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"access_denied\"}").contentType(MediaType.APPLICATION_JSON));

        PollResult p = service.poll("PI-001");

        assertThat(p.status()).isEqualTo("denied");
    }

    @Test
    void poll_approved_verifiesIdTokenViaJwksAndReturnsIdentity() {
        KeyPair kp = Jwts.SIG.RS256.keyPair().build();
        String kid = "test-kid";
        String idToken = Jwts.builder()
                .header().keyId(kid).and()
                .issuer(BASE)
                .audience().add(CLIENT_ID).and()
                .claim("email", "jane@edge.ae")
                .claim("name", "Jane Doe")
                .claim("x4auth:department", "Ops")
                .signWith(kp.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // Standard JWKS JSON (kty/n/e) for the public key, matched by kid.
        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        String n = b64url(toUnsigned(pub.getModulus().toByteArray()));
        String e = b64url(toUnsigned(pub.getPublicExponent().toByteArray()));
        String jwksJson = "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\""
                + kid + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";

        // 1) /oauth/token returns the signed id_token  2) JWKS endpoint serves the public key
        server.expect(requestTo(BASE + "/oauth/token"))
                .andRespond(withSuccess("{\"id_token\":\"" + idToken + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/.well-known/jwks.json"))
                .andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON));

        PollResult p = service.poll("PI-001");

        assertThat(p.approved()).isTrue();
        assertThat(p.email()).isEqualTo("jane@edge.ae");
        assertThat(p.displayName()).isEqualTo("Jane Doe");
        assertThat(p.department()).isEqualTo("Ops");
        server.verify();
    }

    @Test
    void pollExtractsEmployeeIdClaim() throws Exception {
        KeyPair kp = Jwts.SIG.RS256.keyPair().build();
        String kid = "test-kid-empid";
        String idToken = Jwts.builder()
                .header().keyId(kid).and()
                .issuer(BASE)
                .audience().add(CLIENT_ID).and()
                .claim("email", "jane@edge.ae")
                .claim("name", "Jane Doe")
                .claim("x4auth:department", "Ops")
                .claim("x4auth:employeeId", "135727")
                .signWith(kp.getPrivate(), Jwts.SIG.RS256)
                .compact();

        RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
        String n = b64url(toUnsigned(pub.getModulus().toByteArray()));
        String e = b64url(toUnsigned(pub.getPublicExponent().toByteArray()));
        String jwksJson = "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\""
                + kid + "\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";

        server.expect(requestTo(BASE + "/oauth/token"))
                .andRespond(withSuccess("{\"id_token\":\"" + idToken + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/.well-known/jwks.json"))
                .andRespond(withSuccess(jwksJson, MediaType.APPLICATION_JSON));

        X4AuthService.PollResult result = service.poll("PI-002");

        assertThat(result.approved()).isTrue();
        assertThat(result.employeeId()).isEqualTo("135727");
        server.verify();
    }

    private static String b64url(byte[] b) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** BigInteger.toByteArray() prepends a 0x00 sign byte when the high bit is set; JWK n/e are unsigned. */
    private static byte[] toUnsigned(byte[] b) {
        if (b.length > 1 && b[0] == 0) {
            byte[] t = new byte[b.length - 1];
            System.arraycopy(b, 1, t, 0, t.length);
            return t;
        }
        return b;
    }
}
