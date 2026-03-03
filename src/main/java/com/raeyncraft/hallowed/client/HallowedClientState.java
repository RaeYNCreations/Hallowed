package com.raeyncraft.hallowed.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side cache of Hallowed state and visual configuration.
 * Updated whenever a {@link com.raeyncraft.hallowed.network.HallowedSyncPayload} is received.
 * All fields are safe to read on the render thread.
 */
@OnlyIn(Dist.CLIENT)
public final class HallowedClientState {

    private HallowedClientState() {}

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private static volatile boolean hallowed = false;
    private static volatile boolean blueOverlayEnabled = true;
    private static volatile float overlayIntensity = 0.5f;
    private static volatile boolean spectralRendering = true;

    /** Whether the transition flash animation is active. */
    private static volatile long transitionStartMs = -1L;
    /** Duration of the transition flash in milliseconds. */
    private static final long TRANSITION_DURATION_MS = 1_500L;

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public static boolean isHallowed() { return hallowed; }
    public static boolean isBlueOverlayEnabled() { return blueOverlayEnabled; }
    public static float getOverlayIntensity() { return overlayIntensity; }
    public static boolean isSpectralRendering() { return spectralRendering; }

    /**
     * Returns the transition alpha (0.0–1.0) for the death-to-hallowed flash.
     * Returns 0.0 when no transition is active.
     */
    public static float getTransitionAlpha() {
        if (transitionStartMs < 0) return 0.0f;
        long elapsed = System.currentTimeMillis() - transitionStartMs;
        if (elapsed >= TRANSITION_DURATION_MS) {
            transitionStartMs = -1L;
            return 0.0f;
        }
        // Fade out: starts at 1.0, decays to 0.0
        return 1.0f - (float) elapsed / TRANSITION_DURATION_MS;
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Called by the network handler on the client thread when a sync payload arrives.
     */
    public static void update(boolean newHallowed, boolean newOverlayEnabled,
                              float newIntensity, boolean newSpectral) {
        boolean wasHallowed = hallowed;
        hallowed = newHallowed;
        blueOverlayEnabled = newOverlayEnabled;
        overlayIntensity = newIntensity;
        spectralRendering = newSpectral;

        // Trigger transition effect when entering Hallowed state
        if (!wasHallowed && newHallowed) {
            transitionStartMs = System.currentTimeMillis();
        }
    }

    /** Resets state on logout/disconnect. */
    public static void reset() {
        hallowed = false;
        blueOverlayEnabled = true;
        overlayIntensity = 0.5f;
        spectralRendering = true;
        transitionStartMs = -1L;
    }
}
