package com.edge.pulse.data.dto.safrecon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SafReconTokenResponse(String token, long expiresIn) {}
