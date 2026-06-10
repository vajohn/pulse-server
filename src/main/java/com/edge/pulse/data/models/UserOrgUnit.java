package com.edge.pulse.data.models;

import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_org_unit")
@IdClass(UserOrgUnitId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString(exclude = {"user", "orgUnit"})
public class UserOrgUnit {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_unit_id", nullable = false)
    private OrganizationalUnit orgUnit;

    @Column(name = "is_leader", nullable = false)
    private boolean isLeader;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;
}
