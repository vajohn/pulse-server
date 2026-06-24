package com.edge.pulse.data.models.psychometric;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class CapabilityProfileCurrentId implements Serializable {
    private UUID userId;
    private UUID scaleId;
}
