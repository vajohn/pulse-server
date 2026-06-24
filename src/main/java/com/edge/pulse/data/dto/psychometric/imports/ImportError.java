package com.edge.pulse.data.dto.psychometric.imports;

public record ImportError(String file, String row, String column, String message) {}
