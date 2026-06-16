package com.edge.pulse.data.id;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UuidV7GeneratorTest {

    @Test
    void generatesVersion7Variant2() {
        UUID u = UuidV7Generator.generateV7();
        assertEquals(7, u.version(), "must be UUID version 7");
        assertEquals(2, u.variant(), "must be RFC 4122/9562 variant");
    }

    @Test
    void timestampIsNonDecreasingOverTime() throws InterruptedException {
        UUID a = UuidV7Generator.generateV7();
        Thread.sleep(3);
        UUID b = UuidV7Generator.generateV7();
        long tsA = a.getMostSignificantBits() >>> 16; // top 48 bits = unix ms
        long tsB = b.getMostSignificantBits() >>> 16;
        assertTrue(tsB >= tsA, "later id must carry a >= timestamp");
    }

    @Test
    void generatesDistinctValues() {
        UUID a = UuidV7Generator.generateV7();
        UUID b = UuidV7Generator.generateV7();
        assertNotEquals(a, b);
    }
}
