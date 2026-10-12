package com.stockforecast.admin.application;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {

    @Test
    void neverStoresCredentialsInMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("email", "ada@example.com");
        metadata.put("temporaryPassword", "Temporary-Password-42");
        metadata.put("apiKey", "secret");
        metadata.put("accessToken", "eyJ...");
        metadata.put("nested", Map.of("refresh_token", "x", "role", "ADMIN"));
        metadata.put("symbols", List.of("AAPL", "MSFT"));
        metadata.put("count", 3);
        metadata.put("requirePasswordChange", true);

        Map<String, Object> clean = AuditLog.sanitize(metadata);

        assertThat(clean).containsOnlyKeys("email", "nested", "symbols", "count", "requirePasswordChange");
        assertThat(clean.get("nested")).isEqualTo(Map.of("role", "ADMIN"));
        assertThat(clean.get("symbols")).isEqualTo(List.of("AAPL", "MSFT"));
    }
}
