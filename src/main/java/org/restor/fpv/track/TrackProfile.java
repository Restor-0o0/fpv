package org.restor.fpv.track;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public record TrackProfile(
    int gateCount,
    int radius,
    int height,
    int gateSize,
    int laps,
    int candidateAttempts,
    double radialJitter,
    double angularJitter,
    double maxSlope,
    int startPlatformRadius,
    int startFlatness,
    double clearanceRadius,
    double segmentSampleStep
) {
    public static TrackProfile fromJson(JsonObject json) {
        rejectUnknownFields(json);
        TrackProfile profile = new TrackProfile(
            integer(json, "gate_count"),
            integer(json, "radius"),
            integer(json, "height"),
            integer(json, "gate_size"),
            integer(json, "laps"),
            integer(json, "candidate_attempts"),
            decimal(json, "radial_jitter"),
            decimal(json, "angular_jitter"),
            decimal(json, "max_slope"),
            integer(json, "start_platform_radius"),
            integer(json, "start_flatness"),
            decimal(json, "clearance_radius"),
            decimal(json, "segment_sample_step")
        );
        profile.validate();
        return profile;
    }

    private void validate() {
        range("gate_count", gateCount, 4, 32);
        range("radius", radius, 16, 128);
        range("height", height, 4, 32);
        range("gate_size", gateSize, 2, 15);
        range("laps", laps, 1, 50);
        range("candidate_attempts", candidateAttempts, 1, 64);
        range("radial_jitter", radialJitter, 0.0, 0.35);
        range("angular_jitter", angularJitter, 0.0, 0.35);
        range("max_slope", maxSlope, 0.0, 2.0);
        range("start_platform_radius", startPlatformRadius, 1, 8);
        range("start_flatness", startFlatness, 0, 4);
        range("clearance_radius", clearanceRadius, 0.5, 6.0);
        range("segment_sample_step", segmentSampleStep, 0.5, 4.0);
        double chord = 2.0 * radius * Math.sin(Math.PI / (gateCount + 1.0));
        if (chord < gateSize + clearanceRadius * 2.0) {
            throw new JsonParseException("gate_count/radius produce overlapping gates");
        }
    }

    private static int integer(JsonObject json, String name) {
        JsonElement value = required(json, name);
        try {
            int result = value.getAsInt();
            if (value.getAsDouble() != result) throw new NumberFormatException();
            return result;
        } catch (RuntimeException e) {
            throw new JsonParseException(name + " must be an integer", e);
        }
    }

    private static double decimal(JsonObject json, String name) {
        JsonElement value = required(json, name);
        try {
            double result = value.getAsDouble();
            if (!Double.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (RuntimeException e) {
            throw new JsonParseException(name + " must be a finite number", e);
        }
    }

    private static JsonElement required(JsonObject json, String name) {
        JsonElement value = json.get(name);
        if (value == null || value.isJsonNull()) throw new JsonParseException("Missing " + name);
        return value;
    }

    private static void rejectUnknownFields(JsonObject json) {
        for (String name : json.keySet()) {
            if (!switch (name) {
                case "gate_count", "radius", "height", "gate_size", "laps", "candidate_attempts",
                    "radial_jitter", "angular_jitter", "max_slope", "start_platform_radius",
                    "start_flatness", "clearance_radius", "segment_sample_step" -> true;
                default -> false;
            }) throw new JsonParseException("Unknown track profile field: " + name);
        }
    }

    private static void range(String name, int value, int min, int max) {
        if (value < min || value > max) throw new JsonParseException(name + " must be in [" + min + ", " + max + "]");
    }

    private static void range(String name, double value, double min, double max) {
        if (value < min || value > max) throw new JsonParseException(name + " must be in [" + min + ", " + max + "]");
    }
}
