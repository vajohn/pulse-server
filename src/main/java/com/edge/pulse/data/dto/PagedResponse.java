package com.edge.pulse.data.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        boolean hasMore
) {}
