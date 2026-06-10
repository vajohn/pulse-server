package com.edge.pulse.data.models;

import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserOrgUnitId implements Serializable {

    private UUID user;
    private UUID orgUnit;
}
