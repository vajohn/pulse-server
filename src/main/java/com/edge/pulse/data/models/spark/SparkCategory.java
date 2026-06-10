package com.edge.pulse.data.models.spark;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spark_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SparkCategory {

    @Id
    @EqualsAndHashCode.Include
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String icon;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // Field named 'active' so Lombok generates isActive() per Java bean convention.
    // Spring Data then resolves findByActiveTrueOrder... correctly.
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
