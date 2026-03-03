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
        return handler.getStoredMoney().containsValue(cost);
    }

    public static boolean withdrawFunds(Player player, MoneyValue cost) {
        var handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return false;
        MoneyValue simResult = handler.extractMoney(cost, true);
        if (simResult.isEmpty()) return false;
        handler.extractMoney(cost, false);
        return true;
    }

    public static MoneyValue getPlayerBalance(Player player) {
        var handler = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        if (handler == null) return MoneyValue.empty();
        return handler.getStoredMoney().allValues().stream()
                .findFirst().orElse(MoneyValue.empty());
    }

    public static boolean isCoinItem(Player player, ItemStack stack) {
        return false;
    }
}