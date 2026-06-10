package com.edge.pulse.services;

import com.edge.pulse.data.dto.OrgTreeNodeDto;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.mappers.OrgUnitMapper;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final OrganizationalUnitRepository orgUnitRepository;
    private final OrgUnitMapper orgUnitMapper;

    @Transactional(readOnly = true)
    public List<OrgTreeNodeDto> getOrgTree() {
        // Load all active org units in one query, then build tree in-memory.
        // This avoids N+1 queries that would occur with recursive lazy loading of children.
        List<OrganizationalUnit> all = orgUnitRepository.findAllByActiveTrue();
        return orgUnitMapper.buildTree(all);
    }
}
