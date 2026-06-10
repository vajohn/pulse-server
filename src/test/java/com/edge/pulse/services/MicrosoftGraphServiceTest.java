package com.edge.pulse.services;

import com.edge.pulse.data.dto.GraphUserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MicrosoftGraphServiceTest {

    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final String ME_URL = GRAPH_BASE
            + "/me?$select=id,displayName,mail,jobTitle,department,employeeId,officeLocation,companyName";
    private static final String MANAGER_URL = GRAPH_BASE + "/me/manager?$select=id,displayName,mail";
    private static final String TOKEN = "test-access-token";

    private MockRestServiceServer mockServer;
    private MicrosoftGraphService graphService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        graphService = new MicrosoftGraphService(builder, "test-tenant", "test-client", "test-secret");
    }

    // --- fetchMyProfile tests ---

    @Test
    void fetchMyProfile_success_returnsProfile() {
        mockServer.expect(requestTo(ME_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {
                          "id": "azure-id-123",
                          "displayName": "John Doe",
                          "mail": "john@edge.com",
                          "jobTitle": "Engineer",
                          "department": "Engineering",
                          "employeeId": "EMP001",
                          "officeLocation": "Dubai",
                          "companyName": "EDGE"
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<GraphUserProfile> result = graphService.fetchMyProfile(TOKEN);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("azure-id-123");
        assertThat(result.get().department()).isEqualTo("Engineering");
        assertThat(result.get().employeeId()).isEqualTo("EMP001");
        assertThat(result.get().displayName()).isEqualTo("John Doe");
        mockServer.verify();
    }

    @Test
    void fetchMyProfile_serverError_returnsEmpty() {
        mockServer.expect(requestTo(ME_URL))
                .andRespond(withServerError());

        Optional<GraphUserProfile> result = graphService.fetchMyProfile(TOKEN);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void fetchMyProfile_forbidden_returnsEmpty() {
        mockServer.expect(requestTo(ME_URL))
                .andRespond(withForbiddenRequest());

        Optional<GraphUserProfile> result = graphService.fetchMyProfile(TOKEN);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void fetchMyProfile_notFound_returnsEmpty() {
        mockServer.expect(requestTo(ME_URL))
                .andRespond(withResourceNotFound());

        Optional<GraphUserProfile> result = graphService.fetchMyProfile(TOKEN);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    // --- fetchMyManager tests ---

    @Test
    void fetchMyManager_success_returnsManager() {
        mockServer.expect(requestTo(MANAGER_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("""
                        {
                          "id": "manager-azure-id",
                          "displayName": "Jane Manager",
                          "mail": "jane@edge.com"
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<GraphUserProfile> result = graphService.fetchMyManager(TOKEN);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("manager-azure-id");
        assertThat(result.get().displayName()).isEqualTo("Jane Manager");
        assertThat(result.get().mail()).isEqualTo("jane@edge.com");
        mockServer.verify();
    }

    @Test
    void fetchMyManager_serverError_returnsEmpty() {
        mockServer.expect(requestTo(MANAGER_URL))
                .andRespond(withServerError());

        Optional<GraphUserProfile> result = graphService.fetchMyManager(TOKEN);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void fetchMyManager_forbidden_returnsEmpty() {
        mockServer.expect(requestTo(MANAGER_URL))
                .andRespond(withForbiddenRequest());

        Optional<GraphUserProfile> result = graphService.fetchMyManager(TOKEN);

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    void fetchMyManager_notFound_returnsEmpty() {
        mockServer.expect(requestTo(MANAGER_URL))
                .andRespond(withResourceNotFound());

        Optional<GraphUserProfile> result = graphService.fetchMyManager(TOKEN);

        assertThat(result).isEmpty();
        mockServer.verify();
    }
}
