package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request body for {@code POST /api/psychometric/sessions}. */
public record StartPsychometricSessionRequest(@NotNull UUID surveyId) {}
