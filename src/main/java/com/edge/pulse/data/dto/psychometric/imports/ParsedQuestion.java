package com.edge.pulse.data.dto.psychometric.imports;

import java.util.List;

public record ParsedQuestion(String header, String bodyEn, String bodyAr, List<ParsedOption> options) {}
