package com.edge.pulse.services;

import com.edge.pulse.configs.SafReconProperties;
import com.edge.pulse.data.dto.safrecon.SafReconEmployeePage;
import com.edge.pulse.data.dto.safrecon.SafReconOrgUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SafReconClientTest {

    private SafReconClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        SafReconProperties props = new SafReconProperties();
        props.setBaseUrl("http://saf-recon:8081");
        props.setUsername("pulse");
        props.setPassword("secret");
        props.setPageSize(2);

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SafReconClient(props, builder);
    }

    @Test
    void fetchOrgUnits_authenticatesThenReturnsList() {
        server.expect(requestTo("http://saf-recon:8081/api/v1/auth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"token\":\"T1\",\"expiresIn\":900}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://saf-recon:8081/api/v1/org-units"))
                .andExpect(header("Authorization", "Bearer T1"))
                .andRespond(withSuccess("[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"sfCode\":\"X\",\"name\":\"X\",\"level\":\"GROUP\",\"parentId\":null,\"path\":\"\",\"depth\":0,\"companyCode\":null}]", MediaType.APPLICATION_JSON));

        List<SafReconOrgUnit> units = client.fetchOrgUnits();

        assertThat(units).hasSize(1);
        assertThat(units.get(0).sfCode()).isEqualTo("X");
        server.verify();
    }

    @Test
    void fetchOrgUnits_reusesCachedToken() {
        String body = "[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"sfCode\":\"X\",\"name\":\"X\",\"level\":\"GROUP\",\"parentId\":null,\"path\":\"\",\"depth\":0,\"companyCode\":null}]";
        server.expect(requestTo("http://saf-recon:8081/api/v1/auth/token"))
                .andRespond(withSuccess("{\"token\":\"T1\",\"expiresIn\":900}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://saf-recon:8081/api/v1/org-units"))
                .andExpect(header("Authorization", "Bearer T1"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://saf-recon:8081/api/v1/org-units"))
                .andExpect(header("Authorization", "Bearer T1"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        client.fetchOrgUnits();
        client.fetchOrgUnits();   // second call must reuse the cached token (no second auth)

        server.verify();          // exactly 1 auth + 2 data calls; a re-auth would break ordering
    }

    @Test
    void authFailure_propagates() {
        server.expect(requestTo("http://saf-recon:8081/api/v1/auth/token"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.fetchOrgUnits())
                .isInstanceOf(HttpClientErrorException.class);
        server.verify();
    }

    @Test
    void fetchEmployeesPage_refreshesTokenOn401ThenRetries() {
        // initial token
        server.expect(requestTo("http://saf-recon:8081/api/v1/auth/token"))
                .andRespond(withSuccess("{\"token\":\"OLD\",\"expiresIn\":900}", MediaType.APPLICATION_JSON));
        // first data call → 401 (token expired server-side)
        server.expect(requestTo("http://saf-recon:8081/api/v1/employees?page=0&size=2"))
                .andExpect(header("Authorization", "Bearer OLD"))
                .andRespond(withUnauthorizedRequest());
        // re-auth
        server.expect(requestTo("http://saf-recon:8081/api/v1/auth/token"))
                .andRespond(withSuccess("{\"token\":\"NEW\",\"expiresIn\":900}", MediaType.APPLICATION_JSON));
        // retried data call → 200
        server.expect(requestTo("http://saf-recon:8081/api/v1/employees?page=0&size=2"))
                .andExpect(header("Authorization", "Bearer NEW"))
                .andRespond(withSuccess("{\"content\":[],\"totalElements\":0}", MediaType.APPLICATION_JSON));

        SafReconEmployeePage page = client.fetchEmployeesPage(0, 2);

        assertThat(page.totalElements()).isZero();
        server.verify();
    }
}
