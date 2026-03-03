package com.raeyncraft.hallowed;

import com.raeyncraft.hallowed.util.RespawnMode;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Central configuration for the Hallowed mod.
 * All values are validated at load time via NeoForge's config spec system.
 */
public final class HallowedConfig {

    // -------------------------------------------------------------------------
    // Server-side config spec and instance
    // -------------------------------------------------------------------------

    public static final ModConfigSpec SERVER_SPEC;
    public static final HallowedConfig SERVER;

    static {
        var pair = new ModConfigSpec.Builder().configure(HallowedConfig::new);
        SERVER_SPEC = pair.getRight();
        SERVER = pair.getLeft();
    }

    // -------------------------------------------------------------------------
    // Respawn
    // -------------------------------------------------------------------------

    private final ModConfigSpec.EnumValue<RespawnMode> respawnMode;
    private final ModConfigSpec.IntValue resurrectionDelaySeconds;

    // -------------------------------------------------------------------------
    // Resurrection costs
    // -------------------------------------------------------------------------

    private final ModConfigSpec.IntValue baseCost;
    private final ModConfigSpec.IntValue otherBaseCost;
    private final ModConfigSpec.IntValue levelMultiplier;
    private final ModConfigSpec.BooleanValue scalingEnabled;
    private final ModConfigSpec.IntValue factorNumber;

    // -------------------------------------------------------------------------
    // Combat
    // -------------------------------------------------------------------------

    private final ModConfigSpec.BooleanValue combatEnabled;
    private final ModConfigSpec.DoubleValue damageMultiplier;
    private final ModConfigSpec.BooleanValue hostileOnly;
    private final ModConfigSpec.BooleanValue allowLoot;
    private final ModConfigSpec.BooleanValue allowXp;

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    private final ModConfigSpec.BooleanValue allowFlight;
    private final ModConfigSpec.BooleanValue allowNoclip;

    // -------------------------------------------------------------------------
    // Visual
    // -------------------------------------------------------------------------

    private final ModConfigSpec.BooleanValue blueOverlayEnabled;
    private final ModConfigSpec.DoubleValue overlayIntensity;
    private final ModConfigSpec.BooleanValue spectralRendering;

    // -------------------------------------------------------------------------
    // Hardcore
    // -------------------------------------------------------------------------

    private final ModConfigSpec.BooleanValue overrideHardcore;

    // -------------------------------------------------------------------------
    // Admin
    // -------------------------------------------------------------------------

    private final ModConfigSpec.BooleanValue allowCommands;

    // -------------------------------------------------------------------------
    // Compatibility
    // -------------------------------------------------------------------------

    private final ModConfigSpec.BooleanValue enableYouDiedIntegration;

    // -------------------------------------------------------------------------
    // Constructor (called by the spec builder)
    // -------------------------------------------------------------------------

    private HallowedConfig(ModConfigSpec.Builder builder) {

        builder.comment("Respawn behaviour when a player enters the Hallowed state.")
               .push("respawn");
        respawnMode = builder
                .comment("How the player is repositioned after death is intercepted.",
                         "IN_PLACE – player stays at the death location.",
                         "LAST_BONFIRE – teleport to last-used Bonfire (falls back to bed/worldspawn).",
                         "BED_OR_WORLDSPAWN – teleport to bed spawn or world spawn.")
                .defineEnum("mode", RespawnMode.LAST_BONFIRE);
        resurrectionDelaySeconds = builder
                .comment("Seconds the player must wait before they can be resurrected (>= 0).")
                .defineInRange("resurrection_delay_seconds", 0, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Resurrection cost settings (Lightman's Currency integration, Phase 2+).")
               .push("resurrection");
        baseCost = builder
                .comment("Base coin cost to resurrect (>= 0).")
                .defineInRange("base_cost", 10, 0, Integer.MAX_VALUE);
        otherBaseCost = builder
                .comment("Alternative base cost used in certain resurrection scenarios (>= 0).")
                .defineInRange("other_base_cost", 10, 0, Integer.MAX_VALUE);
        levelMultiplier = builder
                .comment("Additional cost per XP level at the time of death (>= 0).")
                .defineInRange("level_multiplier", 0, 0, Integer.MAX_VALUE);
        scalingEnabled = builder
                .comment("Whether resurrection cost scales with player level.")
                .define("scaling_enabled", false);
        factorNumber = builder
                .comment("Divisor used when scaling cost calculations (>= 1).")
                .defineInRange("factor_number", 1, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Combat behaviour for Hallowed (ghost) players.")
               .push("combat");
        combatEnabled = builder
                .comment("If false, Hallowed players cannot deal any damage.")
                .define("enabled", false);
        damageMultiplier = builder
                .comment("Multiplier applied to damage dealt by Hallowed players (>= 0.0).")
                .defineInRange("damage_multiplier", 0.1, 0.0, Double.MAX_VALUE);
        hostileOnly = builder
                .comment("If true, Hallowed players may only attack hostile mobs (ignored when combat.enabled is false).")
                .define("hostile_only", true);
        allowLoot = builder
                .comment("If false, mobs killed by a Hallowed player drop no loot.")
                .define("allow_loot", false);
        allowXp = builder
                .comment("If false, mobs killed by a Hallowed player grant no XP.")
                .define("allow_xp", false);
        builder.pop();

        builder.comment("Movement permissions for Hallowed players.")
               .push("movement");
        allowFlight = builder
                .comment("Whether Hallowed players are allowed to fly.")
                .define("allow_flight", false);
        allowNoclip = builder
                .comment("Whether Hallowed players pass through blocks (noclip).")
                .define("allow_noclip", false);
        builder.pop();

        builder.comment("Visual / HUD settings (client-side effect triggers are server-initiated).")
               .push("visual");
        blueOverlayEnabled = builder
                .comment("Show a blue vignette overlay while the player is Hallowed.")
                .define("blue_overlay_enabled", true);
        overlayIntensity = builder
                .comment("Intensity of the blue overlay (0.0 – 1.0).")
                .defineInRange("overlay_intensity", 0.5, 0.0, 1.0);
        spectralRendering = builder
                .comment("Apply spectral/translucent rendering to Hallowed players.")
                .define("spectral_rendering", true);
        builder.pop();

        builder.comment("Hardcore mode interaction.")
               .push("hardcore");
        overrideHardcore = builder
                .comment("If true, Hallowed intercepts deaths even in hardcore worlds.")
                .define("override_hardcore", true);
        builder.pop();

        builder.comment("Administrative settings.")
               .push("admin");
        allowCommands = builder
                .comment("If true, operators may use /hallowed commands.")
                .define("allow_commands", true);
        builder.pop();

        builder.comment("Third-party mod compatibility switches.")
               .push("compatibility");
        enableYouDiedIntegration = builder
                .comment("Enable integration with the 'You Died' mod when it is present.")
                .define("enable_you_died_integration", true);
        builder.pop();
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    public RespawnMode getRespawnMode() { return respawnMode.get(); }
    public int getResurrectionDelaySeconds() { return resurrectionDelaySeconds.get(); }

    public int getBaseCost() { return baseCost.get(); }
    public int getOtherBaseCost() { return otherBaseCost.get(); }
    public int getLevelMultiplier() { return levelMultiplier.get(); }
    public boolean isScalingEnabled() { return scalingEnabled.get(); }
    public int getFactorNumber() { return factorNumber.get(); }

    public boolean isCombatEnabled() { return combatEnabled.get(); }
    public double getDamageMultiplier() { return damageMultiplier.get(); }
    public boolean isHostileOnly() { return hostileOnly.get(); }
    public boolean isAllowLoot() { return allowLoot.get(); }
    public boolean isAllowXp() { return allowXp.get(); }

    public boolean isAllowFlight() { return allowFlight.get(); }
    public boolean isAllowNoclip() { return allowNoclip.get(); }

    public boolean isBlueOverlayEnabled() { return blueOverlayEnabled.get(); }
    public double getOverlayIntensity() { return overlayIntensity.get(); }
    public boolean isSpectralRendering() { return spectralRendering.get(); }

    public boolean isOverrideHardcore() { return overrideHardcore.get(); }

    public boolean isAllowCommands() { return allowCommands.get(); }

    public boolean isEnableYouDiedIntegration() { return enableYouDiedIntegration.get(); }

    // -------------------------------------------------------------------------
    // Registration helper
    // -------------------------------------------------------------------------

    /**
     * Registers the server config spec with the given mod container.
     * Called from the main mod class constructor.
     */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }
}
