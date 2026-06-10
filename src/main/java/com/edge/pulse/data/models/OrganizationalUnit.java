package com.edge.pulse.data.models;

import com.edge.pulse.data.enums.OrgLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "organizational_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"parent", "children"})
public class OrganizationalUnit {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrganizationalUnit parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<OrganizationalUnit> children;

    @Column(name = "org_unit_name", nullable = false)
    private String orgUnitName;

    @Column(name = "org_unit_code", unique = true)
    private String orgUnitCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_level", nullable = false)
    private OrgLevel orgLevel;

    @Column(nullable = false)
    @Builder.Default
    private String path = "";

    @Column(nullable = false)
    @Builder.Default
    private int depth = 0;

    private String entraGroupId;

    /** SF SuccessFactors company code — used for SCOPE_ENTITY filtering. */
    @Column(name = "company_code")
    private String companyCode;

    /** SF SuccessFactors external code (e.g. division/department code).
     *  Not unique — the same code can appear at multiple org levels. */
    @Column(name = "sf_external_code")
    private String sfExternalCode;

    /** Source of the last sync that created/updated this record: MANUAL, ENTRA, SF. */
    @Column(name = "sync_source", nullable = false)
    @Builder.Default
    private String syncSource = "MANUAL";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
