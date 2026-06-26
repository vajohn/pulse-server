package com.edge.pulse.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.edge.pulse.data.dto.psychometric.InstrumentDto;
import com.edge.pulse.data.dto.psychometric.TestTypeCapabilityDto;
import com.edge.pulse.data.enums.Measures;
import com.edge.pulse.services.psychometric.CadenceAdminService;
import com.edge.pulse.services.psychometric.InstrumentService;
import com.edge.pulse.services.psychometric.PsychometricAdminService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;

class AdminPsychometricCatalogControllerTest {

    private PsychometricAdminService adminService;
    private InstrumentService instrumentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(PsychometricAdminService.class);
        CadenceAdminService cadence = Mockito.mock(CadenceAdminService.class);
        instrumentService = Mockito.mock(InstrumentService.class);
        com.edge.pulse.services.psychometric.TestApprovalService approvalService =
                Mockito.mock(com.edge.pulse.services.psychometric.TestApprovalService.class);
        com.edge.pulse.mappers.psychometric.TestApprovalMapper approvalMapper =
                Mockito.mock(com.edge.pulse.mappers.psychometric.TestApprovalMapper.class);
        AdminPsychometricController controller = new AdminPsychometricController(
                adminService, cadence, instrumentService, approvalService, approvalMapper);
        mockMvc = standaloneSetup(controller).build();
    }

    @Test
    void testTypes_returnsEnrichedCatalog() throws Exception {
        when(adminService.listTestTypeCapabilities()).thenReturn(List.of(
                new TestTypeCapabilityDto("PERSONALITY", "Personality", "desc", Measures.TYPICAL,
                        List.of("Big Five"), false, false, List.of("SCALE")),
                new TestTypeCapabilityDto("COGNITIVE", "Cognitive ability", "desc", Measures.MAXIMAL,
                        List.of("Logical Reasoning"), true, true, List.of("CHOICE_SINGLE")),
                new TestTypeCapabilityDto("COMPETENCY", "Competency", "desc", Measures.DERIVED,
                        List.of("Competency framework"), false, false, List.of())));

        mockMvc.perform(get("/api/admin/psychometric/test-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].value").value("PERSONALITY"))
                .andExpect(jsonPath("$[0].measures").value("TYPICAL"))
                .andExpect(jsonPath("$[0].exampleInstruments[0]").value("Big Five"))
                .andExpect(jsonPath("$[2].value").value("COMPETENCY"))
                .andExpect(jsonPath("$[2].allowedQuestionTypes.length()").value(0));
    }

    @Test
    void instruments_returnsSuggestions() throws Exception {
        when(instrumentService.list()).thenReturn(List.of(
                new InstrumentDto(UUID.randomUUID(), "PTI Plus", "ptiplus", 3L)));

        mockMvc.perform(get("/api/admin/psychometric/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("PTI Plus"))
                .andExpect(jsonPath("$[0].canonicalName").value("ptiplus"))
                .andExpect(jsonPath("$[0].testCount").value(3));
    }
}
