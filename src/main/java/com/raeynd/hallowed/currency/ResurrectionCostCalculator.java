package com.raeynd.hallowed.currency;

import com.raeynd.hallowed.HallowedConfig;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;

/**
 * Calculates resurrection costs using the config formula:
 * <pre>
 *   Cost = baseCost + (level × levelMultiplier / factorNumber)   [when scaling enabled]
 *   Cost = baseCost                                              [when scaling disabled]
 * </pre>
 * Division is integer division, matching the config spec.
 */
public final class ResurrectionCostCalculator {

    private ResurrectionCostCalculator() {}

    /**
     * Self-resurrection cost (uses {@code resurrection.base_cost}).
     */
    public static long calculateSelfCost(int xpLevel) {
        HallowedConfig cfg = HallowedConfig.SERVER;
        long base = cfg.getBaseCost();
        if (cfg.isScalingEnabled()) {
            base += ((long) xpLevel * cfg.getLevelMultiplier()) / cfg.getFactorNumber();
        }
        return base;
    }

    /**
     * Other-player resurrection cost (uses {@code resurrection.other_base_cost}).
     */
    public static long calculateOtherCost(int xpLevel) {
        HallowedConfig cfg = HallowedConfig.SERVER;
        long base = cfg.getOtherBaseCost();
        if (cfg.isScalingEnabled()) {
            base += ((long) xpLevel * cfg.getLevelMultiplier()) / cfg.getFactorNumber();
        }
        return base;
    }

    /**
     * Self-resurrection cost as a {@link MoneyValue} ready for LC API calls.
     */
    public static MoneyValue calculateSelfCostAsValue(int xpLevel) {
        return CurrencyService.createCoinValue(calculateSelfCost(xpLevel));
    }

    /**
     * Other-player resurrection cost as a {@link MoneyValue} ready for LC API calls.
     */
    public static MoneyValue calculateOtherCostAsValue(int xpLevel) {
        return CurrencyService.createCoinValue(calculateOtherCost(xpLevel));
    }
}
