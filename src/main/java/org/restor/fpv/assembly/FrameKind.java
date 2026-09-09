package org.restor.fpv.assembly;

// Multirotor frames hover on their motor thrust; fixed-wing frames need airspeed over a
// wing. The kind decides which motors and props fit and which entity the bench builds.
public enum FrameKind {
    MULTIROTOR,
    FIXED_WING
}
