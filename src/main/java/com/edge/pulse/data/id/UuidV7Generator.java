package com.edge.pulse.data.id;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Generates RFC 9562 UUID version 7 (48-bit Unix-ms timestamp prefix + random tail).
 * Zero external dependencies — chosen so the air-gapped k2 fat-jar build stays simple.
 */
public class UuidV7Generator implements BeforeExecutionGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
                           Object currentValue, EventType eventType) {
        if (currentValue != null) {
            return currentValue;
        }
        return generateV7();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    public static UUID generateV7() {
        long ts = System.currentTimeMillis();
        byte[] b = new byte[16];

        // bytes 0..5: 48-bit big-endian timestamp
        b[0] = (byte) (ts >>> 40);
        b[1] = (byte) (ts >>> 32);
        b[2] = (byte) (ts >>> 24);
        b[3] = (byte) (ts >>> 16);
        b[4] = (byte) (ts >>> 8);
        b[5] = (byte) ts;

        // bytes 6..15: random
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);
        System.arraycopy(rand, 0, b, 6, 10);

        // version 7 in high nibble of byte 6
        b[6] = (byte) ((b[6] & 0x0F) | 0x70);
        // variant 10xx in high bits of byte 8
        b[8] = (byte) ((b[8] & 0x3F) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (b[i] & 0xFF);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (b[i] & 0xFF);
        return new UUID(msb, lsb);
    }
}
