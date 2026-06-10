package com.edge.pulse.data.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "form_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"form", "orgUnit", "user", "assignedBy"})
public class FormAssignment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private Form form;

    /** If set, the form is assigned to all users in this org unit (and its descendants). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_unit_id")
    private OrganizationalUnit orgUnit;

    /** If set, the form is assigned to this specific user. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    /** When the assignment becomes visible to users. Null = immediately. */
    private LocalDateTime startsAt;

    /** When the assignment expires and is no longer visible. Null = never expires. */
    private LocalDateTime expiresAt;

    /** Soft deadline shown to users. Does not hide the form. */
    private LocalDateTime dueDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean mandatory = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Whether this cascades to child org units. Only relevant when orgUnit is set. */
    @Column(nullable = false)
    @Builder.Default
    private boolean includeChildren = true;

    /** If true, the user can submit multiple response sessions (e.g., recurring feedback). */
    @Column(nullable = false)
    @Builder.Default
    private boolean allowResubmission = false;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
}
