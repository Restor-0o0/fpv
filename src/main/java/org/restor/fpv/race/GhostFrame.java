package org.restor.fpv.race;

// One tick of recorded flight telemetry. The frame index within a GhostRecording is the
// tick offset from the run's start, so no explicit tick field is stored. Throttle is kept
// only to spin the ghost's props; position/attitude reproduce the motion exactly.
public record GhostFrame(double x, double y, double z, float yaw, float pitch, float roll, float throttle) {}
