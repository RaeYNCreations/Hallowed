package com.raeyncraft.hallowed.currency;

import io.github.lightman314.lightmanscurrency.api.money.MoneyAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CurrencyService {

    private CurrencyService() {}

    public static MoneyValue createCoinValue(long coreValue) {
        return CoinValue.fromNumber("main", coreValue);
    }

    public static boolean hasEnoughFunds(Player player, MoneyValue cost) {
        var handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return false;
        // FIX: getStoredMoney() returns MoneyView, not an Iterable<MoneyValue>
        // MoneyView.containsValue() checks if the stored money covers the given cost
        return handler.getStoredMoney().containsValue(cost);
    }

    public static boolean withdrawFunds(Player player, MoneyValue cost) {
        var handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return false;
        // Use containsValue for the check instead of relying on simulation return value
        if (!handler.getStoredMoney().containsValue(cost)) return false;
        handler.extractMoney(cost, false);
        return true;
    }

    public static MoneyValue getPlayerBalance(Player player) {
        var handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return MoneyValue.empty();
        // FIX: MoneyView has no .stream() — use allValues() which returns List<MoneyValue>
        return handler.getStoredMoney().allValues().stream()
                .findFirst().orElse(MoneyValue.empty());
    }

    public static boolean isCoinItem(Player player, ItemStack stack) {
        return false;
    }
}