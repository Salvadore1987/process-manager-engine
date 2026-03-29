package uz.salvadore.processengine.core.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

public final class UUIDv7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UUIDv7() {
    }

    public static UUID generate() {
        long timestamp = Instant.now().toEpochMilli();

        long msb = (timestamp << 16) & 0xFFFFFFFFFFFF0000L;
        msb |= 0x7000L;
        msb |= (long) (RANDOM.nextInt() & 0x0FFF);

        long lsb = RANDOM.nextLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }
}
