package com.edge.pulse.mappers;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SparkReferenceTest {

    @Test
    void formatsLastEightHexUpperWithPrefix() {
        UUID id = UUID.fromString("01902d3c-7a3f-7c12-9b4e-0a1b2c3d4e5f");
        assertEquals("NOM-2C3D4E5F", SparkReference.format(id));
    }

    @Test
    void usesTrailingRandomBitsNotLeadingTimestamp() {
        UUID a = UUID.fromString("01902d3c-7a3f-7c12-9b4e-0a1b2c3daaaa");
        UUID b = UUID.fromString("01902d3c-7a3f-7c12-9b4e-0a1b2c3dbbbb");
        assertEquals("NOM-2C3DAAAA", SparkReference.format(a));
        assertEquals("NOM-2C3DBBBB", SparkReference.format(b));
    }
}
