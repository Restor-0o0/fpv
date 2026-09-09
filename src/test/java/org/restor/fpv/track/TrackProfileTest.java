package org.restor.fpv.track;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrackProfileTest {
    private static final String VALID = """
        {
          "gate_count": 8,
          "radius": 42,
          "height": 8,
          "gate_size": 6,
          "laps": 3,
          "candidate_attempts": 16,
          "radial_jitter": 0.14,
          "angular_jitter": 0.12,
          "max_slope": 0.45,
          "start_platform_radius": 3,
          "start_flatness": 1,
          "clearance_radius": 2.0,
          "segment_sample_step": 1.5
        }
        """;

    @Test
    void parsesValidProfile() {
        TrackProfile profile = TrackProfile.fromJson(JsonParser.parseString(VALID).getAsJsonObject());
        assertEquals(8, profile.gateCount());
        assertEquals(42, profile.radius());
    }

    @Test
    void rejectsMissingAndUnknownFields() {
        assertThrows(JsonParseException.class, () -> TrackProfile.fromJson(
            JsonParser.parseString(VALID.replace("\"height\": 8,", "")).getAsJsonObject()));
        assertThrows(JsonParseException.class, () -> TrackProfile.fromJson(
            JsonParser.parseString(VALID.replace("\"gate_count\": 8,", "\"gate_count\": 8, \"typo\": 1,")).getAsJsonObject()));
    }

    @Test
    void rejectsImpossibleGateDensity() {
        assertThrows(JsonParseException.class, () -> TrackProfile.fromJson(
            JsonParser.parseString(VALID.replace("\"gate_count\": 8", "\"gate_count\": 32")
                .replace("\"radius\": 42", "\"radius\": 16")).getAsJsonObject()));
    }
}
