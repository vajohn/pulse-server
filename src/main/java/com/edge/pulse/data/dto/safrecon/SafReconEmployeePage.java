package com.edge.pulse.data.dto.safrecon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Spring Data Page projection — only the fields we consume. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SafReconEmployeePage(List<SafReconEmployee> content, long totalElements) {}
