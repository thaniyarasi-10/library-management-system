package com.kovanlabs.librarymanagement.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class JacksonCommonConfigTest {

    private final JacksonCommonConfig config = new JacksonCommonConfig();

    record SampleRecord(String name, LocalDate date) {}
    record PartialRecord(String name) {}

    @Test
    void objectMapper_shouldBeInstantiatedAndConfigured() throws Exception {
        ObjectMapper objectMapper = config.objectMapper();
        assertNotNull(objectMapper);

        SampleRecord sample = new SampleRecord("Antigravity", LocalDate.of(2026, 9, 23));
        String json = objectMapper.writeValueAsString(sample);

        assertTrue(json.contains("\"date\":\"2026-09-23\""));

        SampleRecord deserialized = objectMapper.readValue(json, SampleRecord.class);
        assertEquals(sample, deserialized);
    }

    @Test
    void objectMapper_shouldIgnoreUnknownProperties() throws Exception {
        ObjectMapper objectMapper = config.objectMapper();
        String json = "{\"name\":\"Antigravity\",\"extraField\":\"unknownValue\"}";

        PartialRecord partial = objectMapper.readValue(json, PartialRecord.class);
        assertNotNull(partial);
        assertEquals("Antigravity", partial.name());
    }
}
