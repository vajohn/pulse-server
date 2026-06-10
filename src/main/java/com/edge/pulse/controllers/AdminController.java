package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.OrgTreeNodeDto;
import com.edge.pulse.services.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Shared admin endpoints (org tree, roles list, etc.)
 * User/questionnaire/survey/report/approval endpoints moved to dedicated controllers.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/org-tree")
    @PreAuthorize("hasAnyAuthority('USR_READ', 'FORM_ASSIGN', 'REPORT_VIEW', 'ORG_READ')")
    public ResponseEntity<List<OrgTreeNodeDto>> getOrgTree() {
        return ResponseEntity.ok(adminService.getOrgTree());
    }
}
