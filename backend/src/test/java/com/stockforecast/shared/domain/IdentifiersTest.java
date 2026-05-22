package com.stockforecast.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifiersTest {

    @Test
    void generatesRfc9562Version7Uuids() {
        UUID id = Identifiers.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheCurrentUnixTimestampInTheMostSignificantBits() {
        long before = System.currentTimeMillis();
        UUID id = Identifiers.newId();
        long after = System.currentTimeMillis();

        long embeddedMillis = id.getMostSignificantBits() >>> 16;
        assertThat(embeddedMillis).isBetween(before, after);
    }

    @Test
    void generatesUniqueIdentifiers() {
        Set<UUID> ids = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> ids.add(Identifiers.newId()));

        assertThat(ids).hasSize(10_000);
    }
}
