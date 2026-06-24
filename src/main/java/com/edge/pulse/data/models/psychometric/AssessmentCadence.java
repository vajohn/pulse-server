package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.Cadence;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assessment_cadence")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"test", "orgUnit", "createdBy"})
public class AssessmentCadence {

    @Id @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "test_id", nullable = false)
    private PsychometricTest test;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Cadence cadence;

    @Column(name = "max_items_per_admin", nullable = false)
    @Builder.Default
    private int maxItemsPerAdmin = 12;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrganizationalUnit orgUnit;

    @Column(name = "include_children", nullable = false)
    @Builder.Default
    private boolean includeChildren = true;

    @Column(name = "starts_at") private LocalDateTime startsAt;
    @Column(name = "ends_at")   private LocalDateTime endsAt;

    @Column(nullable = false) @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false) @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
