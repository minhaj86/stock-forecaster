package com.stockforecast.shared.domain;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates RFC 9562 version 7 UUIDs: a 48-bit Unix-millisecond prefix followed by 74 random bits.
 *
 * <p>Time-ordered identifiers keep B-tree primary-key indexes append-mostly (unlike random v4 UUIDs) while
 * remaining unguessable, and they let aggregates own their identity before they are persisted.
 */
public final class Identifiers {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Identifiers() {
    }

    public static UUID newId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        long epochMillis = System.currentTimeMillis();
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) (epochMillis >>> (40 - 8 * i));
        }
        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
