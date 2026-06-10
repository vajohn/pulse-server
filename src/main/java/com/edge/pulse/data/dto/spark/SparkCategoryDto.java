package com.edge.pulse.data.dto.spark;

public record SparkCategoryDto(
        String id,
        String name,
        String description,
        String icon,
        int displayOrder,
        boolean isActive
) {}
