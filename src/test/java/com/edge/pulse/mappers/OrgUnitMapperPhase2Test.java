package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.OrgTreeNodeDto;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.OrganizationalUnit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 (2-C): verifies that OrgUnitMapper.buildTree() constructs the org tree
 * from a flat list in-memory, avoiding N+1 recursive lazy loads.
 */
class OrgUnitMapperPhase2Test {

    private final OrgUnitMapper mapper = new OrgUnitMapper();

    @Test
    void buildTree_emptyList_returnsEmptyRoots() {
        assertThat(mapper.buildTree(List.of())).isEmpty();
    }

    @Test
    void buildTree_singleRoot_noChildren() {
        OrganizationalUnit root = unit(null, "HQ", OrgLevel.ENTITY, 0);

        List<OrgTreeNodeDto> result = mapper.buildTree(List.of(root));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orgUnitName()).isEqualTo("HQ");
        assertThat(result.get(0).children()).isEmpty();
    }

    @Test
    void buildTree_rootWithChildren_hierarchyAssembled() {
        OrganizationalUnit root = unit(null, "Engineering", OrgLevel.ENTITY, 0);
        OrganizationalUnit child1 = unit(root, "Backend", OrgLevel.TEAM, 1);
        OrganizationalUnit child2 = unit(root, "Frontend", OrgLevel.TEAM, 1);
        OrganizationalUnit grandchild = unit(child1, "API Squad", OrgLevel.TEAM, 2);

        List<OrgTreeNodeDto> result = mapper.buildTree(List.of(root, child1, child2, grandchild));

        assertThat(result).hasSize(1);
        OrgTreeNodeDto rootDto = result.get(0);
        assertThat(rootDto.orgUnitName()).isEqualTo("Engineering");
        assertThat(rootDto.children()).hasSize(2);

        OrgTreeNodeDto backendDto = rootDto.children().stream()
                .filter(c -> "Backend".equals(c.orgUnitName())).findFirst().orElseThrow();
        assertThat(backendDto.children()).hasSize(1);
        assertThat(backendDto.children().get(0).orgUnitName()).isEqualTo("API Squad");

        OrgTreeNodeDto frontendDto = rootDto.children().stream()
                .filter(c -> "Frontend".equals(c.orgUnitName())).findFirst().orElseThrow();
        assertThat(frontendDto.children()).isEmpty();
    }

    @Test
    void buildTree_multipleRoots_allReturnedAtTopLevel() {
        OrganizationalUnit root1 = unit(null, "Engineering", OrgLevel.ENTITY, 0);
        OrganizationalUnit root2 = unit(null, "Operations", OrgLevel.ENTITY, 0);
        OrganizationalUnit child = unit(root1, "Backend", OrgLevel.TEAM, 1);

        List<OrgTreeNodeDto> result = mapper.buildTree(List.of(root1, root2, child));

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(OrgTreeNodeDto::orgUnitName))
                .containsExactlyInAnyOrder("Engineering", "Operations");
    }

    private OrganizationalUnit unit(OrganizationalUnit parent, String name, OrgLevel level, int depth) {
        return OrganizationalUnit.builder()
                .id(UUID.randomUUID())
                .parent(parent)
                .orgUnitName(name)
                .orgUnitCode(name.substring(0, 2).toUpperCase())
                .orgLevel(level)
                .depth(depth)
                .active(true)
                .build();
    }
}
