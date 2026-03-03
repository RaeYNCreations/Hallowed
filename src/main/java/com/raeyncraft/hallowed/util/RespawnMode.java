package com.raeyncraft.hallowed.util;

/**
 * Defines how a Hallowed player is repositioned after death is intercepted.
 */
public enum RespawnMode {
    /** Player stays at the exact location where they died. */
    IN_PLACE,

    /** Player is teleported to their last-used Bonfire, falling back to bed or world spawn. */
    LAST_BONFIRE,

    /** Player is teleported to their bed spawn or the world spawn. */
    BED_OR_WORLDSPAWN
}
