package com.edge.pulse.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphGroup(
    String id,
    String displayName,
    String description
) {}
