package com.raeyncraft.hallowed.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side cache of Hallowed state and visual configuration.
 * Updated whenever a HallowedSyncPayload is received.
 * All fields are safe to read on the render thread.
 */
@OnlyIn(Dist.CLIENT)
public final class HallowedClientState {

    private HallowedClientState() {}

    private static volatile boolean hallowed = false;
    private static volatile boolean blueOverlayEnabled = true;
    private static volatile float overlayIntensity = 0.5f;
    private static volatile boolean spectralRendering = true;
    private static volatile float spectralAlpha = 0.45f;
    private static volatile long transitionStartMs = -1L;
    private static final long TRANSITION_DURATION_MS = 1_500L;

    public static boolean isHallowed() { return hallowed; }
    public static boolean isBlueOverlayEnabled() { return blueOverlayEnabled; }
    public static float getOverlayIntensity() { return overlayIntensity; }
    public static boolean isSpectralRendering() { return spectralRendering; }
    public static float getSpectralAlpha() { return spectralAlpha; }
    public static float getTransitionAlpha() {
        if (transitionStartMs < 0) return 0.0f;
        long elapsed = System.currentTimeMillis() - transitionStartMs;
        if (elapsed >= TRANSITION_DURATION_MS) {
            transitionStartMs = -1L;
            return 0.0f;
        }
        return 1.0f - (float) elapsed / TRANSITION_DURATION_MS;
    }

    public static void update(boolean newHallowed, boolean newOverlayEnabled,
                              float newIntensity, boolean newSpectral, float newSpectralAlpha) {
        boolean wasHallowed = hallowed;
        hallowed = newHallowed;
        blueOverlayEnabled = newOverlayEnabled;
        overlayIntensity = newIntensity;
        spectralRendering = newSpectral;
        spectralAlpha = newSpectralAlpha;

        if (!wasHallowed && newHallowed) {
            transitionStartMs = System.currentTimeMillis();
        }
    }

    public static void reset() {
        hallowed = false;
        blueOverlayEnabled = true;
        overlayIntensity = 0.5f;
        spectralRendering = true;
        spectralAlpha = 0.45f;
        transitionStartMs = -1L;
    }
}