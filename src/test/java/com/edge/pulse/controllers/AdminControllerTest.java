package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.OrgTreeNodeDto;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.services.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private MockMvc mockMvc;
    @Mock private AdminService adminService;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(adminService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getOrgTree_returnsTree() throws Exception {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        List<OrgTreeNodeDto> tree = List.of(
                new OrgTreeNodeDto(rootId, "Engineering", "ENG", OrgLevel.ENTITY, 0, List.of(
                        new OrgTreeNodeDto(childId, "Team A", "TA", OrgLevel.TEAM, 1, List.of())
                ))
        );
        when(adminService.getOrgTree()).thenReturn(tree);

        mockMvc.perform(get("/api/admin/org-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orgUnitName").value("Engineering"))
                .andExpect(jsonPath("$[0].children[0].orgUnitName").value("Team A"));
    }

    @Test
    void getOrgTree_returnsEmptyWhenNoRoots() throws Exception {
        when(adminService.getOrgTree()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/org-tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
