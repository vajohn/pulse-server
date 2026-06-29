package com.edge.pulse.data.dto.psychometric;

import java.util.UUID;

/** Response shape for an uploaded psychometric asset. */
public record AssetRefDto(UUID id, String url, String contentType, int byteSize) {}
