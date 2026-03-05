package com.raeyncraft.hallowed;

import com.raeyncraft.hallowed.util.RespawnMode;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HallowedConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final HallowedConfig SERVER;

    static {
        var pair = new ModConfigSpec.Builder().configure(HallowedConfig::new);
        SERVER_SPEC = pair.getRight();
        SERVER = pair.getLeft();
    }

    private final ModConfigSpec.EnumValue<RespawnMode> respawnMode;
    private final ModConfigSpec.IntValue resurrectionDelaySeconds;
    private final ModConfigSpec.IntValue baseCost;
    private final ModConfigSpec.IntValue otherBaseCost;
    private final ModConfigSpec.IntValue levelMultiplier;
    private final ModConfigSpec.BooleanValue scalingEnabled;
    private final ModConfigSpec.IntValue factorNumber;
    private final ModConfigSpec.BooleanValue combatEnabled;
    private final ModConfigSpec.DoubleValue damageMultiplier;
    private final ModConfigSpec.BooleanValue hostileOnly;
    private final ModConfigSpec.BooleanValue allowLoot;
    private final ModConfigSpec.BooleanValue allowXp;
    private final ModConfigSpec.BooleanValue allowFlight;
    private final ModConfigSpec.BooleanValue allowBlockBreak;
    private final ModConfigSpec.BooleanValue allowBlockPlace;
    private final ModConfigSpec.BooleanValue blueOverlayEnabled;
    private final ModConfigSpec.DoubleValue overlayIntensity;
    private final ModConfigSpec.BooleanValue spectralRendering;
    private final ModConfigSpec.BooleanValue overrideHardcore;
    private final ModConfigSpec.BooleanValue allowCommands;
    private final ModConfigSpec.BooleanValue enableYouDiedIntegration;
    private final ModConfigSpec.BooleanValue burnInSunlight;
    private final ModConfigSpec.BooleanValue allowOnlyCoinLoot;
    private final ModConfigSpec.DoubleValue spectralAlpha;

    // Runtime overrides — survive config reloads. Null = use config file value.
    private RespawnMode overrideRespawnMode = null;
    private Integer overrideResurrectionDelaySeconds = null;
    private Integer overrideBaseCost = null;
    private Integer overrideOtherBaseCost = null;
    private Integer overrideLevelMultiplier = null;
    private Boolean overrideScalingEnabled = null;
    private Integer overrideFactorNumber = null;
    private Boolean overrideCombatEnabled = null;
    private Double overrideDamageMultiplier = null;
    private Boolean overrideHostileOnly = null;
    private Boolean overrideAllowLoot = null;
    private Boolean overrideAllowXp = null;
    private Boolean overrideAllowFlight = null;
    private Boolean overrideAllowBlockBreak = null;
    private Boolean overrideAllowBlockPlace = null;
    private Boolean overrideBlueOverlayEnabled = null;
    private Double overrideOverlayIntensity = null;
    private Boolean overrideSpectralRendering = null;
    private Boolean overrideOverrideHardcore = null;
    private Boolean overrideEnableYouDiedIntegration = null;
    private Boolean overrideBurnInSunlight = null;
    private Boolean overrideAllowOnlyCoinLoot = null;
    private Double overrideSpectralAlpha = null;

    private HallowedConfig(ModConfigSpec.Builder builder) {
        builder.comment("Respawn behaviour when a player enters the Hallowed state.").push("respawn");
        respawnMode = builder.comment("IN_PLACE, LAST_BONFIRE, or BED_OR_WORLDSPAWN.").defineEnum("mode", RespawnMode.LAST_BONFIRE);
        resurrectionDelaySeconds = builder.comment("Seconds before resurrection is allowed (>= 0).").defineInRange("resurrection_delay_seconds", 0, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Resurrection cost settings.").push("resurrection");
        baseCost = builder.comment("Base coin cost for self-resurrection (>= 0).").defineInRange("base_cost", 10, 0, Integer.MAX_VALUE);
        otherBaseCost = builder.comment("Base coin cost for other-player resurrection (>= 0).").defineInRange("other_base_cost", 10, 0, Integer.MAX_VALUE);
        levelMultiplier = builder.comment("Extra cost per XP level (>= 0).").defineInRange("level_multiplier", 0, 0, Integer.MAX_VALUE);
        scalingEnabled = builder.comment("Whether cost scales with player level.").define("scaling_enabled", false);
        factorNumber = builder.comment("Divisor for scaling calculations (>= 1).").defineInRange("factor_number", 1, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.comment("Combat behaviour for Hallowed players.").push("combat");
        combatEnabled = builder.comment("If false, Hallowed players cannot deal damage.").define("enabled", false);
        damageMultiplier = builder.comment("Damage multiplier for Hallowed players (>= 0.0).").defineInRange("damage_multiplier", 0.1, 0.0, Double.MAX_VALUE);
        hostileOnly = builder.comment("If true, Hallowed players may only attack hostile mobs.").define("hostile_only", true);
        allowLoot = builder.comment("If false, mobs killed by Hallowed players drop no loot.").define("allow_loot", false);
        allowXp = builder.comment("If false, mobs killed by Hallowed players grant no XP.").define("allow_xp", false);
        builder.pop();

        builder.comment("Movement permissions for Hallowed players.").push("movement");
        allowFlight = builder.comment("Whether Hallowed players can fly.").define("allow_flight", false);
        builder.pop();

        builder.comment("Interaction permissions for Hallowed players.").push("interaction");
        allowBlockBreak = builder.comment("Whether Hallowed players can break blocks.").define("allow_block_break", false);
        allowBlockPlace = builder.comment("Whether Hallowed players can place blocks.").define("allow_block_place", false);
        allowOnlyCoinLoot = builder.comment("If true, Hallowed players can only pick up coins. If false, they can pick up anything.").define("allow_only_coin_loot", true);
        builder.pop();

        builder.comment("Visual / HUD settings.").push("visual");
        blueOverlayEnabled = builder.comment("Show blue vignette overlay while Hallowed.").define("blue_overlay_enabled", true);
        overlayIntensity = builder.comment("Intensity of the blue overlay (0.0-1.0).").defineInRange("overlay_intensity", 0.5, 0.0, 1.0);
        spectralRendering = builder.comment("Apply translucent rendering to Hallowed players.").define("spectral_rendering", true);
        builder.pop();

        builder.comment("Hardcore mode interaction.").push("hardcore");
        overrideHardcore = builder.comment("If true, Hallowed intercepts deaths in hardcore worlds.").define("override_hardcore", true);
        builder.pop();

        builder.comment("Administrative settings.").push("admin");
        allowCommands = builder.comment("If true, operators may use /hallowed commands.").define("allow_commands", true);
        builder.pop();

        builder.comment("Third-party mod compatibility switches.").push("compatibility");
        enableYouDiedIntegration = builder.comment("Enable You Died mod integration when present.").define("enable_you_died_integration", true);
        builder.pop();

        builder.comment("World interaction settings for Hallowed players.").push("world");
        burnInSunlight = builder.comment("If true, Hallowed players burn in direct sunlight like undead mobs.").define("burn_in_sunlight", false);
        builder.pop();
    }

    // Getters — runtime override takes priority over config file
    public RespawnMode getRespawnMode() { return overrideRespawnMode != null ? overrideRespawnMode : respawnMode.get(); }
    public int getResurrectionDelaySeconds() { return overrideResurrectionDelaySeconds != null ? overrideResurrectionDelaySeconds : resurrectionDelaySeconds.get(); }
    public int getBaseCost() { return overrideBaseCost != null ? overrideBaseCost : baseCost.get(); }
    public int getOtherBaseCost() { return overrideOtherBaseCost != null ? overrideOtherBaseCost : otherBaseCost.get(); }
    public int getLevelMultiplier() { return overrideLevelMultiplier != null ? overrideLevelMultiplier : levelMultiplier.get(); }
    public boolean isScalingEnabled() { return overrideScalingEnabled != null ? overrideScalingEnabled : scalingEnabled.get(); }
    public int getFactorNumber() { return overrideFactorNumber != null ? overrideFactorNumber : factorNumber.get(); }
    public boolean isCombatEnabled() { return overrideCombatEnabled != null ? overrideCombatEnabled : combatEnabled.get(); }
    public double getDamageMultiplier() { return overrideDamageMultiplier != null ? overrideDamageMultiplier : damageMultiplier.get(); }
    public boolean isHostileOnly() { return overrideHostileOnly != null ? overrideHostileOnly : hostileOnly.get(); }
    public boolean isAllowLoot() { return overrideAllowLoot != null ? overrideAllowLoot : allowLoot.get(); }
    public boolean isAllowXp() { return overrideAllowXp != null ? overrideAllowXp : allowXp.get(); }
    public boolean isAllowFlight() { return overrideAllowFlight != null ? overrideAllowFlight : allowFlight.get(); }
    public boolean isAllowBlockBreak() { return overrideAllowBlockBreak != null ? overrideAllowBlockBreak : allowBlockBreak.get(); }
    public boolean isAllowBlockPlace() { return overrideAllowBlockPlace != null ? overrideAllowBlockPlace : allowBlockPlace.get(); }
    public boolean isBlueOverlayEnabled() { return overrideBlueOverlayEnabled != null ? overrideBlueOverlayEnabled : blueOverlayEnabled.get(); }
    public double getOverlayIntensity() { return overrideOverlayIntensity != null ? overrideOverlayIntensity : overlayIntensity.get(); }
    public boolean isSpectralRendering() { return overrideSpectralRendering != null ? overrideSpectralRendering : spectralRendering.get(); }
    public boolean isOverrideHardcore() { return overrideOverrideHardcore != null ? overrideOverrideHardcore : overrideHardcore.get(); }
    public boolean isAllowCommands() { return allowCommands.get(); }
    public boolean isEnableYouDiedIntegration() { return overrideEnableYouDiedIntegration != null ? overrideEnableYouDiedIntegration : enableYouDiedIntegration.get(); }
    public boolean isBurnInSunlight() { return overrideBurnInSunlight != null ? overrideBurnInSunlight : burnInSunlight.get(); }
    public boolean isAllowOnlyCoinLoot() { return overrideAllowOnlyCoinLoot != null ? overrideAllowOnlyCoinLoot : allowOnlyCoinLoot.get(); }
    public double getSpectralAlpha() { return overrideSpectralAlpha != null ? overrideSpectralAlpha : spectralAlpha.get(); }

    // Setters — store in runtime override field AND write to disk
    public void setRespawnMode(RespawnMode v) { overrideRespawnMode = v; respawnMode.set(v); SERVER_SPEC.save(); }
    public void setResurrectionDelaySeconds(int v) { overrideResurrectionDelaySeconds = v; resurrectionDelaySeconds.set(v); SERVER_SPEC.save(); }
    public void setBaseCost(int v) { overrideBaseCost = v; baseCost.set(v); SERVER_SPEC.save(); }
    public void setOtherBaseCost(int v) { overrideOtherBaseCost = v; otherBaseCost.set(v); SERVER_SPEC.save(); }
    public void setLevelMultiplier(int v) { overrideLevelMultiplier = v; levelMultiplier.set(v); SERVER_SPEC.save(); }
    public void setScalingEnabled(boolean v) { overrideScalingEnabled = v; scalingEnabled.set(v); SERVER_SPEC.save(); }
    public void setFactorNumber(int v) { overrideFactorNumber = v; factorNumber.set(v); SERVER_SPEC.save(); }
    public void setCombatEnabled(boolean v) { overrideCombatEnabled = v; combatEnabled.set(v); SERVER_SPEC.save(); }
    public void setDamageMultiplier(double v) { overrideDamageMultiplier = v; damageMultiplier.set(v); SERVER_SPEC.save(); }
    public void setHostileOnly(boolean v) { overrideHostileOnly = v; hostileOnly.set(v); SERVER_SPEC.save(); }
    public void setAllowLoot(boolean v) { overrideAllowLoot = v; allowLoot.set(v); SERVER_SPEC.save(); }
    public void setAllowXp(boolean v) { overrideAllowXp = v; allowXp.set(v); SERVER_SPEC.save(); }
    public void setAllowFlight(boolean v) { overrideAllowFlight = v; allowFlight.set(v); SERVER_SPEC.save(); }
    public void setAllowBlockBreak(boolean v) { overrideAllowBlockBreak = v; allowBlockBreak.set(v); SERVER_SPEC.save(); }
    public void setAllowBlockPlace(boolean v) { overrideAllowBlockPlace = v; allowBlockPlace.set(v); SERVER_SPEC.save(); }
    public void setBlueOverlayEnabled(boolean v) { overrideBlueOverlayEnabled = v; blueOverlayEnabled.set(v); SERVER_SPEC.save(); }
    public void setOverlayIntensity(double v) { overrideOverlayIntensity = v; overlayIntensity.set(v); SERVER_SPEC.save(); }
    public void setSpectralRendering(boolean v) { overrideSpectralRendering = v; spectralRendering.set(v); SERVER_SPEC.save(); }
    public void setOverrideHardcore(boolean v) { overrideOverrideHardcore = v; overrideHardcore.set(v); SERVER_SPEC.save(); }
    public void setEnableYouDiedIntegration(boolean v) { overrideEnableYouDiedIntegration = v; enableYouDiedIntegration.set(v); SERVER_SPEC.save(); }
    public void setBurnInSunlight(boolean v) { overrideBurnInSunlight = v; burnInSunlight.set(v); SERVER_SPEC.save(); }
    public void setAllowOnlyCoinLoot(boolean v) { overrideAllowOnlyCoinLoot = v; allowOnlyCoinLoot.set(v); SERVER_SPEC.save(); }
    public void setSpectralAlpha(double v) { overrideSpectralAlpha = v; spectralAlpha.set(v); SERVER_SPEC.save(); }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }
}