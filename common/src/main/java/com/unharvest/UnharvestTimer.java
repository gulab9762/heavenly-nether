package com.unharvest;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * High-precision harvest speedrun timer (3-millisecond precision).
 * Displays time in mm:ss:xxx (or hh:mm:ss:xxx for long/AFK runs).
 * Automatically freezes when the window is unfocused, when any screen is open,
 * or when the game is paused.
 */
public class UnharvestTimer {

    private static long lastUpdateNano = -1;
    private static boolean isFrozen = true;

    /**
     * Updates elapsed time each frame. Called from HUD render loop.
     */
    public static synchronized void update(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            lastUpdateNano = -1;
            isFrozen = true;
            return;
        }

        if (!UnharvestManager.isUnharvestWorld(client.level)) {
            lastUpdateNano = -1;
            isFrozen = true;
            return;
        }

        UnharvestState state = UnharvestState.getInstance();

        // If field generation is currently underway, timer remains stopped at 0
        if (UnharvestFieldGenerator.isGenerating()) {
            lastUpdateNano = -1;
            isFrozen = true;
            return;
        }

        // If 100% field is completed, timer is permanently locked at final time
        if (state.isFieldCompleted()) {
            lastUpdateNano = -1;
            isFrozen = true;
            return;
        }

        // Freeze if unfocused, if any screen is open (e.g. Chat, Pause, Inventory, Upgrades), or if paused
        boolean screenOpen = client.gui != null && client.gui.screen() != null;
        boolean windowActive = client.isWindowActive();
        boolean paused = client.isPaused();

        if (screenOpen || !windowActive || paused) {
            lastUpdateNano = -1;
            isFrozen = true;
            return;
        }

        long now = System.nanoTime();
        if (lastUpdateNano < 0 || isFrozen) {
            lastUpdateNano = now;
            isFrozen = false;
            return;
        }

        long deltaNano = now - lastUpdateNano;
        lastUpdateNano = now;

        // Discard massive deltas (>500ms) from system lag/sleep
        if (deltaNano > 0 && deltaNano < 500_000_000L) {
            long ms = deltaNano / 1_000_000L;
            if (ms > 0) {
                state.addElapsedMillis(ms);
                // Preserve fractional nanos to prevent timing drift
                lastUpdateNano -= (deltaNano % 1_000_000L);
            }
        }
    }

    public static boolean isFrozen() {
        return isFrozen;
    }

    public static synchronized void reset() {
        lastUpdateNano = -1;
        isFrozen = true;
        UnharvestState.getInstance().setElapsedMillis(0);
    }

    /**
     * Formats milliseconds into mm:ss:xxx or hh:mm:ss:xxx (if hours > 0).
     */
    public static String formatTimer(long totalMillis) {
        if (totalMillis < 0) totalMillis = 0;
        long ms = totalMillis % 1000;
        long totalSeconds = totalMillis / 1000;
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60;
        long minutes = totalMinutes % 60;
        long hours = totalMinutes / 60;

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d:%03d", hours, minutes, seconds, ms);
        } else {
            return String.format(Locale.US, "%02d:%02d:%03d", minutes, seconds, ms);
        }
    }
}

