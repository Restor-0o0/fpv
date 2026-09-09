package org.restor.fpv.assembly;

/**
 * What an airframe is built to do. Beyond the tuning numbers this decides which extra
 * bench slots the frame gets: a spotter opens a strike bay once a targeting module is
 * fitted, a carrier always carries FPV cards, and the strike frames take a warhead.
 */
public enum FrameRole {
    STANDARD,
    SPOTTER,
    CARRIER,
    KAMIKAZE,
    LOITER,
    INTERCEPTOR
}
